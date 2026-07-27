package com.gerwinkuijntjes.hours.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.backup.DavError

/**
 * One sentence per failure, in words that point at what to change.
 *
 * "http 401" tells the person holding the phone nothing; "the user name or app
 * password is wrong" tells them exactly which field to look at.
 */
@Composable
fun davErrorText(error: DavError): String = when (error) {
    is DavError.Unauthorized -> stringResource(R.string.dav_unauthorized)
    is DavError.Forbidden -> stringResource(R.string.dav_forbidden)
    is DavError.NotFound -> stringResource(R.string.dav_not_found)
    is DavError.NotWebDav -> stringResource(R.string.dav_not_webdav)
    is DavError.AlreadyExists -> stringResource(R.string.dav_exists)
    is DavError.OutOfSpace -> stringResource(R.string.dav_out_of_space)
    is DavError.Unreachable -> stringResource(R.string.dav_unreachable)
    is DavError.InsecureConnection -> stringResource(R.string.dav_insecure)
    is DavError.Server -> stringResource(R.string.dav_server, error.code)
    is DavError.Unexpected -> stringResource(R.string.dav_unexpected, error.message)
}
