#!/usr/bin/env python3
import argparse
import torch
from pathlib import Path
from omegaconf import OmegaConf
from datasets import load_from_disk
from transformers import AutoTokenizer, AutoModelForCausalLM


def tensor_to_device(batch, device):
    for k, v in batch.items():
        if isinstance(v, torch.Tensor):
            batch[k] = v.to(device)
    return batch


def stack_or_pad(samples, key, pad_value=0):
    """Stack tensors or pad them if they have different lengths."""
    values = [s[key] for s in samples if key in s]
    if not values:
        return None

    normalized = []
    for v in values:
        if isinstance(v, torch.Tensor):
            t = v
        else:
            t = torch.tensor(v)
        if t.dim() != 1:
            t = t.view(-1)
        normalized.append(t)

    if all(v.shape == normalized[0].shape for v in normalized):
        return torch.stack(normalized)

    max_len = max(v.shape[0] for v in normalized)
    padded = []
    for v in normalized:
        if v.shape[0] < max_len:
            pad_size = max_len - v.shape[0]
            pad_tensor = torch.full((pad_size,), pad_value, dtype=v.dtype)
            v = torch.cat([v, pad_tensor])
        padded.append(v)
    return torch.stack(padded)


def param_norm(model):
    return sum(p.detach().float().norm().item() for p in model.parameters() if p is not None)


def main(cfg_path, batch_size=2, device=None, with_optim=False):
    cfg = OmegaConf.load(cfg_path)
    device = device or ("cuda" if torch.cuda.is_available() else "cpu")

    print(f"Using device: {device}")
    restore_from = cfg.restore_from
    save_root = cfg.save_root

    print(f"Loading tokenizer/model from: {restore_from}")
    tokenizer = AutoTokenizer.from_pretrained(restore_from)
    model = AutoModelForCausalLM.from_pretrained(restore_from).to(device)
    model.train()

    processed_dataset_path = Path(save_root) / "processed_romanian_dataset"
    if not processed_dataset_path.exists():
        raise SystemExit(f"Processed dataset not found at: {processed_dataset_path}")

    print(f"Loading processed dataset from: {processed_dataset_path}")
    ds = load_from_disk(str(processed_dataset_path))
    print(f"Dataset columns: {ds.column_names}, num_rows={ds.num_rows}")

    sample = ds.select(range(min(batch_size, len(ds))))
    
    # Manual batch collation with padding
    batch = {
        'input_ids': stack_or_pad(sample, 'input_ids', pad_value=tokenizer.pad_token_id),
        'labels': stack_or_pad(sample, 'labels', pad_value=-100),
        'attention_mask': stack_or_pad(sample, 'attention_mask', pad_value=0),
    }
    batch = tensor_to_device(batch, device)

    # Print some diagnostics
    print(f'input_ids shape: {batch["input_ids"].shape}')
    print(f'labels shape: {batch["labels"].shape}')
    print(f'attention_mask shape: {batch["attention_mask"].shape}')
    if 'labels' in batch:
        valid_labels = batch['labels'][batch['labels']!=-100]
        print(f'labels (non-padding) unique samples: {torch.unique(valid_labels)[:10].tolist()}')
        print(f'labels % padding: {(batch["labels"]==-100).float().mean():.2%}')

    before_norm = param_norm(model)
    print(f'param norm before step: {before_norm:.6f}')

    # Forward
    outputs = model(**{k: v for k, v in batch.items() if k in ['input_ids', 'attention_mask', 'labels']})
    if hasattr(outputs, 'loss') and outputs.loss is not None:
        loss = outputs.loss
    elif outputs[0] is not None:
        loss = outputs[0]
    else:
        raise ValueError('No loss in model output')
    print(f'forward loss: {loss.item():.6f}')
    print(f'loss requires_grad: {loss.requires_grad}')
    print(f'logits shape: {outputs.logits.shape if hasattr(outputs, "logits") else "N/A"}')

    loss.backward()

    # gradient norms
    grad_norm = 0.0
    for p in model.parameters():
        if p.grad is not None:
            grad_norm += p.grad.detach().float().norm().item()**2
    grad_norm = grad_norm**0.5
    print(f'grad norm: {grad_norm:.6f}')

    if with_optim:
        opt = torch.optim.AdamW(model.parameters(), lr=getattr(cfg, 'lr', 1e-5))
        opt.step()
        opt.zero_grad()
        after_norm = param_norm(model)
        print(f'param norm after step: {after_norm:.6f}')
    else:
        print('Skipping optimizer step (use --with_optim to test updates).')
    print('done')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('config', help='Path to config yaml used by finetune.py')
    parser.add_argument('--batch_size', type=int, default=2)
    parser.add_argument('--device', type=str, default=None)
    parser.add_argument('--with_optim', action='store_true', help='Run optimizer step (can OOM on small GPUs)')
    args = parser.parse_args()
    main(args.config, batch_size=args.batch_size, device=args.device, with_optim=args.with_optim)
