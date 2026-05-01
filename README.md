# Banking Mockup API 사용 가이드

이 문서는 `banking-mockup` 목업 뱅킹 API 서버를 실행하고 호출하는 방법을 설명합니다.

## 1. 실행 전 준비

서버는 RDBMS 없이 Redis만 사용합니다. 권장 실행 방식은 Docker Compose입니다.

## 2. Docker Compose 실행

프로젝트 루트에서 실행합니다.

```bash
docker compose up --build
```

서비스 구성은 다음과 같습니다.

| 서비스 | 이미지/빌드 | 호스트 포트 | 컨테이너 포트 |
| --- | --- | ---: | ---: |
| `mock-banking-api` | local `Dockerfile` build | 8081 | 8080 |
| `mock-redis` | `redis:7.2` | 6380 | 6379 |

Spring Boot 컨테이너는 아래 환경 변수로 내부 Redis에 접속합니다.

```text
SERVER_PORT=8080
REDIS_HOST=mock-redis
REDIS_PORT=6379
MOAJE_BANKING_SECRET=Moaje-banking-secret
```

호스트에서 API를 호출할 때는 `http://localhost:8081`을 사용합니다.

## 3. 로컬 직접 실행

로컬에서 Spring Boot를 직접 실행할 경우 기본 API 포트는 `8081`입니다.

Docker Compose로 Redis만 띄운 상태에서 로컬 앱을 실행하려면 Redis가 호스트 포트 `6380`에 노출되어 있으므로 다음 환경 변수를 사용합니다.

```bash
REDIS_HOST=localhost REDIS_PORT=6380 SERVER_PORT=8081 ./gradlew bootRun
```

Windows PowerShell 예시:

```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6380"
$env:SERVER_PORT="8081"
.\gradlew bootRun
```

현재 프로젝트에 Gradle wrapper가 없다면 Gradle을 설치하거나 wrapper를 생성한 뒤 실행합니다.

## 4. 환경 변수

| 환경 변수 | 기본값 | Docker Compose 값 | 설명 |
| --- | --- | --- | --- |
| `REDIS_HOST` | `localhost` | `mock-redis` | Redis host |
| `REDIS_PORT` | `6379` | `6379` | Redis port. Compose 외부에서 Redis에 접근할 때는 `6380` 사용 |
| `SERVER_PORT` | `8081` | `8080` | Spring Boot server port |
| `MOAJE_BANKING_SECRET` | `Moaje-banking-secret` | `Moaje-banking-secret` | HMAC 서명 공통키 |

## 5. 전체 호출 흐름

1. `/oauth/2.0/token`으로 Access Token을 발급합니다.
2. `/api/**` 요청 Body를 기준으로 HMAC-SHA256 서명을 생성합니다.
3. `Authorization: Bearer {accessToken}`와 `X-Signature: {signature}` 헤더를 넣어 API를 호출합니다.
4. 장애 테스트가 필요하면 `X-Mock-Scenario` 헤더를 추가합니다.

## 6. HMAC 서명 생성

### Node.js

```javascript
const crypto = require("crypto");

const secret = "Moaje-banking-secret";
const body = JSON.stringify({
  bankCode: "088",
  productName: "SurePay 입출금통장",
  initialBalance: 100000
});

const signature = crypto
  .createHmac("sha256", secret)
  .update(Buffer.from(body, "utf8"))
  .digest("hex");

console.log(signature);
```

### Kotlin

```kotlin
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun hmacSha256Hex(body: String, secret: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(body.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
```

GET, DELETE 요청처럼 Body가 없으면 빈 문자열 `""`에 대해 서명합니다.

```text
HMAC-SHA256(secret, "")
```

## 7. curl 호출 예시

아래 예시는 Linux/macOS shell 기준입니다.

### 7.1 Access Token 발급

```bash
curl -X POST "http://localhost:8081/oauth/2.0/token" \
  -H "Content-Type: application/json" \
  -d '{"ci":"CI-20260501-0001","userName":"홍길동","phoneNumber":"01012345678"}'
```

응답의 `accessToken` 값을 이후 요청에 사용합니다.

### 7.2 계좌 등록

```bash
BODY='{"bankCode":"088","productName":"SurePay 입출금통장","initialBalance":100000}'
SIGNATURE=$(printf "%s" "$BODY" | openssl dgst -sha256 -hmac "Moaje-banking-secret" -binary | xxd -p -c 256)

curl -X POST "http://localhost:8081/api/accounts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Signature: ${SIGNATURE}" \
  -d "$BODY"
```

### 7.3 계좌 조회

```bash
SIGNATURE=$(printf "" | openssl dgst -sha256 -hmac "Moaje-banking-secret" -binary | xxd -p -c 256)

curl -X GET "http://localhost:8081/api/accounts/${ACCOUNT_NUMBER}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Signature: ${SIGNATURE}"
```

### 7.4 출금

```bash
BODY='{"amount":10000,"memo":"ATM 출금"}'
SIGNATURE=$(printf "%s" "$BODY" | openssl dgst -sha256 -hmac "Moaje-banking-secret" -binary | xxd -p -c 256)

curl -X POST "http://localhost:8081/api/accounts/${ACCOUNT_NUMBER}/withdrawals" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Signature: ${SIGNATURE}" \
  -d "$BODY"
```

### 7.5 이체

```bash
BODY='{"fromAccountNumber":"'"${FROM_ACCOUNT_NUMBER}"'","toAccountNumber":"'"${TO_ACCOUNT_NUMBER}"'","amount":50000,"memo":"점심 정산"}'
SIGNATURE=$(printf "%s" "$BODY" | openssl dgst -sha256 -hmac "Moaje-banking-secret" -binary | xxd -p -c 256)

curl -X POST "http://localhost:8081/api/transfers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Signature: ${SIGNATURE}" \
  -d "$BODY"
```

### 7.6 거래내역 조회

```bash
SIGNATURE=$(printf "" | openssl dgst -sha256 -hmac "Moaje-banking-secret" -binary | xxd -p -c 256)

curl -X GET "http://localhost:8081/api/accounts/${ACCOUNT_NUMBER}/histories" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Signature: ${SIGNATURE}"
```

### 7.7 계좌 해지

```bash
SIGNATURE=$(printf "" | openssl dgst -sha256 -hmac "Moaje-banking-secret" -binary | xxd -p -c 256)

curl -X DELETE "http://localhost:8081/api/accounts/${ACCOUNT_NUMBER}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Signature: ${SIGNATURE}"
```

## 8. 장애 주입 테스트

장애 주입은 정상적인 `Authorization`, `X-Signature` 검증을 통과한 뒤 적용됩니다.

```bash
curl -X POST "http://localhost:8081/api/transfers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Signature: ${SIGNATURE}" \
  -H "X-Mock-Scenario: CONCURRENCY_CONFLICT" \
  -d "$BODY"
```

사용 가능한 값은 다음과 같습니다.

| 값 | 기대 결과 |
| --- | --- |
| `TIMEOUT_ERROR` | 5초 지연 |
| `INTERNAL_SERVER_ERROR` | 500 |
| `CONCURRENCY_CONFLICT` | 409 |
| `ACCOUNT_FROZEN` | 403 |

## 9. Redis 데이터 확인

Docker Compose로 실행 중이면 호스트에서는 `localhost:6380`으로 Redis에 접근합니다.

```bash
redis-cli -p 6380 GET "auth:token:${ACCESS_TOKEN}"
redis-cli -p 6380 TTL "auth:token:${ACCESS_TOKEN}"
redis-cli -p 6380 HGETALL "{banking}:account:${ACCOUNT_NUMBER}"
redis-cli -p 6380 ZRANGE "{banking}:account:history:${ACCOUNT_NUMBER}" 0 -1
```

## 10. 연동 시 주의사항

- HMAC 서명은 실제 전송하는 Body 문자열과 byte 단위로 일치해야 합니다.
- 공백, 줄바꿈, 필드 순서가 달라지면 서명이 달라질 수 있습니다.
- `/api/**` 요청 Body가 10KB를 초과하면 `413 Payload Too Large`가 반환됩니다.
- Access Token은 Redis TTL로 관리되며 30일 후 자동 만료됩니다.
- 출금과 이체는 Redis Lua Script로 잔액 변경과 거래내역 저장을 원자 처리합니다.
