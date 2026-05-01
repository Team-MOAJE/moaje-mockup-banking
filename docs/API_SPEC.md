# Banking Mockup API 문서

`banking-mockup`은 금융결제원 오픈뱅킹 흐름을 단순화한 목업 뱅킹 API 서버입니다.

## 기본 정보

- Project name: `banking-mockup`
- Docker Compose Base URL: `http://localhost:8081`
- Local default Base URL: `http://localhost:8081`
- Content-Type: `application/json`
- 문자 인코딩: UTF-8
- 일시 형식: ISO-8601 UTC 문자열, 예: `2026-05-01T01:23:45.123Z`

## 인증 및 무결성

`/oauth/2.0/token`을 제외한 `/api/**` API는 아래 헤더가 필요합니다.

| Header | 필수 | 설명 |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식 |
| `X-Signature` | Y | 요청 Body 전체에 대한 HMAC-SHA256 hex 서명 |
| `X-Mock-Scenario` | N | 장애 주입 시나리오 |

### HMAC 서명 규칙

- Secret 설정: `moaje.banking.secret`
- 기본값: `Moaje-banking-secret`
- 알고리즘: `HmacSHA256`
- 서명 대상: HTTP Request Body 원문 byte 배열
- 결과 형식: lowercase hex string
- GET/DELETE처럼 Body가 없으면 빈 문자열 `""`을 서명합니다.

## 인프라 포트

| 대상 | 호스트 포트 | 컨테이너 포트 | 설명 |
| --- | ---: | ---: | --- |
| `mock-banking-api` | 8081 | 8080 | Spring Boot 앱 |
| `mock-redis` | 6380 | 6379 | Redis 7.2 |

Docker Compose에서 Spring Boot 앱은 내부 네트워크의 `mock-redis:6379`로 Redis에 접속합니다.

## 장애 주입

`/api/**` 요청에 `X-Mock-Scenario` 헤더를 지정하면 다음 상황을 모사합니다.

| 값 | HTTP Status | 동작 |
| --- | ---: | --- |
| `TIMEOUT_ERROR` | 비즈니스 API 결과에 따름 | 5초 지연 후 정상 처리 진행 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 오류 강제 반환 |
| `CONCURRENCY_CONFLICT` | 409 | Redis 락 충돌 상황 모사 |
| `ACCOUNT_FROZEN` | 403 | 계좌 정지 상황 모사 |

## API 목록

| 기능 | Method | Path | 인증/HMAC |
| --- | --- | --- | --- |
| Access Token 발급 | POST | `/oauth/2.0/token` | 불필요 |
| 계좌 등록 | POST | `/api/accounts` | 필요 |
| 계좌 해지 | DELETE | `/api/accounts/{accountNumber}` | 필요 |
| 계좌 조회 | GET | `/api/accounts/{accountNumber}` | 필요 |
| 거래내역 조회 | GET | `/api/accounts/{accountNumber}/histories` | 필요 |
| 출금 | POST | `/api/accounts/{accountNumber}/withdrawals` | 필요 |
| 이체 | POST | `/api/transfers` | 필요 |

## 1. Access Token 발급

### Request

```http
POST /oauth/2.0/token
Content-Type: application/json
```

```json
{
  "ci": "CI-20260501-0001",
  "userName": "홍길동",
  "phoneNumber": "01012345678"
}
```

### Response

```http
200 OK
```

```json
{
  "accessToken": "2a73fb5a-b54d-4bb4-8e7e-97b2d8475d9b",
  "tokenType": "Bearer",
  "expiresIn": 2592000
}
```

### Redis 저장

```text
Key: auth:token:{accessToken}
Value: TokenSession JSON
TTL: 30 days
```

## 2. 계좌 등록

### Request

```http
POST /api/accounts
Authorization: Bearer {accessToken}
X-Signature: {hmac}
Content-Type: application/json
```

```json
{
  "bankCode": "088",
  "productName": "SurePay 입출금통장",
  "initialBalance": 100000
}
```

### Response

```http
201 Created
```

```json
{
  "accountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
  "bankCode": "088",
  "productName": "SurePay 입출금통장",
  "balance": 100000,
  "status": "ACTIVE",
  "createdAt": "2026-05-01T01:23:45.123Z",
  "canceledAt": null
}
```

## 3. 계좌 해지

계좌를 물리 삭제하지 않고 상태를 `CANCELED`로 변경합니다.

```http
DELETE /api/accounts/{accountNumber}
Authorization: Bearer {accessToken}
X-Signature: {hmacOfEmptyBody}
```

```json
{
  "accountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
  "bankCode": "088",
  "productName": "SurePay 입출금통장",
  "balance": 100000,
  "status": "CANCELED",
  "createdAt": "2026-05-01T01:23:45.123Z",
  "canceledAt": "2026-05-01T02:00:00.000Z"
}
```

## 4. 계좌 조회

```http
GET /api/accounts/{accountNumber}
Authorization: Bearer {accessToken}
X-Signature: {hmacOfEmptyBody}
```

```json
{
  "accountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
  "bankCode": "088",
  "productName": "SurePay 입출금통장",
  "balance": 100000,
  "status": "ACTIVE",
  "createdAt": "2026-05-01T01:23:45.123Z",
  "canceledAt": null
}
```

## 5. 거래내역 조회

거래내역은 Redis Sorted Set에 거래시각 순서로 저장됩니다.

```http
GET /api/accounts/{accountNumber}/histories
Authorization: Bearer {accessToken}
X-Signature: {hmacOfEmptyBody}
```

```json
[
  {
    "transactionId": "c6b8b2d1-0c6e-4bc5-851c-f8dfde74de82",
    "accountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
    "type": "WITHDRAWAL",
    "amount": 10000,
    "counterpartyAccountNumber": null,
    "counterpartyBankCode": null,
    "memo": "ATM 출금",
    "createdAt": "2026-05-01T01:30:00.000Z"
  }
]
```

## 6. 출금

```http
POST /api/accounts/{accountNumber}/withdrawals
Authorization: Bearer {accessToken}
X-Signature: {hmac}
Content-Type: application/json
```

```json
{
  "amount": 10000,
  "memo": "ATM 출금"
}
```

```json
{
  "accountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
  "bankCode": "088",
  "productName": "SurePay 입출금통장",
  "balance": 90000,
  "status": "ACTIVE",
  "createdAt": "2026-05-01T01:23:45.123Z",
  "canceledAt": null
}
```

## 7. 이체

출금 계좌와 입금 계좌의 잔액 변경, 양쪽 거래내역 저장은 Redis Lua Script로 원자 처리됩니다.

```http
POST /api/transfers
Authorization: Bearer {accessToken}
X-Signature: {hmac}
Content-Type: application/json
```

```json
{
  "fromAccountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
  "toAccountNumber": "b725eacdfb9b480e9ff34d5c1d872f20",
  "amount": 50000,
  "memo": "점심 정산"
}
```

```json
{
  "fromAccountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
  "toAccountNumber": "b725eacdfb9b480e9ff34d5c1d872f20",
  "amount": 50000,
  "fromBalance": 40000,
  "toBalance": 150000,
  "debitHistory": {
    "transactionId": "9460f2f1-f1b8-4023-8f4c-a02fd60ca710",
    "accountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
    "type": "TRANSFER_OUT",
    "amount": 50000,
    "counterpartyAccountNumber": "b725eacdfb9b480e9ff34d5c1d872f20",
    "counterpartyBankCode": null,
    "memo": "점심 정산",
    "createdAt": "2026-05-01T01:40:00.000Z"
  },
  "creditHistory": {
    "transactionId": "4c0fe483-4f23-4e81-9dfb-9c787006bbf5",
    "accountNumber": "b725eacdfb9b480e9ff34d5c1d872f20",
    "type": "TRANSFER_IN",
    "amount": 50000,
    "counterpartyAccountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
    "counterpartyBankCode": null,
    "memo": "점심 정산",
    "createdAt": "2026-05-01T01:40:00.000Z"
  }
}
```

## 오류 응답

비즈니스 예외는 JSON 오류 응답을 반환합니다.

```json
{
  "code": "ACCOUNT_BUSINESS_ERROR",
  "message": "Insufficient balance: f2b1c7a9e6d24c569f61d5d884bf55f2",
  "timestamp": "2026-05-01T01:45:00.000Z"
}
```

| Status | 상황 |
| ---: | --- |
| 400 | 잘못된 금액, 해지 계좌, 잔액 부족, 동일 계좌 이체 |
| 401 | Access Token 누락/만료/오류, HMAC 서명 오류 |
| 403 | `X-Mock-Scenario: ACCOUNT_FROZEN` |
| 404 | 계좌 없음 |
| 409 | `X-Mock-Scenario: CONCURRENCY_CONFLICT` |
| 413 | 요청 Body 10KB 초과 |
| 500 | `X-Mock-Scenario: INTERNAL_SERVER_ERROR` |
