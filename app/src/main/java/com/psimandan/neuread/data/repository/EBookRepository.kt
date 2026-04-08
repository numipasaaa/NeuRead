package com.psimandan.neuread.data.repository

import android.net.Uri
import com.psimandan.neuread.data.datasource.EBookDataSource
import com.psimandan.neuread.data.model.EBookFile
import javax.inject.Inject

class EBookRepository @Inject constructor(
    private val dataSource: EBookDataSource
) {
    suspend fun getEBookFileFromUri(uri: Uri): EBookFile? {
        return dataSource.getEBookFileFromUri(uri)
    }

    suspend fun getEbookFileFromClipboard(): EBookFile? {
        return dataSource.getEbookFileFromClipboard()
    }
}
