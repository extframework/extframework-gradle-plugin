package dev.extframework.gradle

import dev.extframework.tooling.api.exception.ExceptionType

enum class GradleExceptions : ExceptionType {
    NoEntrypoint,
    EntrypointConfigurationFailed
}