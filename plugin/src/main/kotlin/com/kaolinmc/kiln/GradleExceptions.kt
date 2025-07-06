package com.kaolinmc.kiln

import com.kaolinmc.tooling.api.exception.ExceptionType

enum class GradleExceptions : ExceptionType {
    NoEntrypoint,
    EntrypointConfigurationFailed
}