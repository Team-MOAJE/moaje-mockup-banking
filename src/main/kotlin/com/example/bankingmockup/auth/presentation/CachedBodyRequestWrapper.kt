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

/**
 * Request로 요청된 데이터는 단 한번만 읽을 수 있기때문에, body를 caching 하는 wrapper class
 * ContentCachingRequestWrapper : spring이 제공해주는 유틸리티 클래스로 Request를 Wrapper 하여 body를 캐싱할 수 있도록 해줌
 */
class CachedBodyRequestWrapper(
    request: HttpServletRequest,
) : ContentCachingRequestWrapper(request) {
    // 원본 Request 요청으로부터 body를 cachedBody에 담아둔다 (final)
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
        // 사용하지않음.
    }
}
