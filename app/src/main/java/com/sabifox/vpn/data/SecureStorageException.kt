package com.sabifox.vpn.data

class SecureStorageException(cause: Exception) :
    IllegalStateException(
        "Encrypted storage is unavailable. Unlock the device and try again. Stored credentials have not been reset.",
        cause,
    )
