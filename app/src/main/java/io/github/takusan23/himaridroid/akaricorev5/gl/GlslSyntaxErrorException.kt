package io.github.takusan23.himaridroid.akaricorev5.gl

data class GlslSyntaxErrorException(
    val compileErrorMessage: String
) : RuntimeException()