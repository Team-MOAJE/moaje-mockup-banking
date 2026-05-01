package com.example.bankingmockup.auth.presentation

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import org.springframework.util.StreamUtils
import org.springframework.web.util.ContentCachingRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class CachedBodyRequestWrapper(
    request: HttpServletRequest,
) : ContentCachingRequestWrapper(request) {
    val cachedBody: ByteArray = StreamUtils.copyToByteArray(super.getInputStream())

    override fun getInputStream(): ServletInputStream =
        CachedBodyServletInputStream(cachedBody)

    override fun getReader(): BufferedReader {
        val charset = characterEncoding.let(Charset::forName) ?: StandardCharsets.UTF_8
        return BufferedReader(InputStreamReader(inputStream, charset))
    }
}

private class CachedBodyServletInputStream(
    cachedBody: ByteArray,
) : ServletInputStream() {
    private val inputStream = ByteArrayInputStream(cachedBody)

    override fun read(): Int = inputStream.read()

    override fun isFinished(): Boolean = inputStream.available() == 0

    override fun isReady(): Boolean = true

    override fun setReadListener(listener: ReadListener?) {
        // Synchronous mock API; async servlet reads are not used.
    }
}
