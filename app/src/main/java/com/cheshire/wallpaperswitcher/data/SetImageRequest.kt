package com.cheshire.wallpaperswitcher.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SetImageRequest(
    val uri: Uri,
) : Parcelable
