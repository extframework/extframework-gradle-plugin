package com.kaolinmc.kiln

class ReconfigurationException : Exception(
    "The cache fingerprint has been invalidated by changes to this extensions configuration. Caches have been updated. Please retry."
)