# Banking Mockup JSON Request and Response 스펙

이 문서는 `banking-mockup` API의 JSON 필드 정의를 정리합니다.

## 공통 규칙

- `amount`, `balance`, `initialBalance`는 정수입니다.
- 금액은 원화 기준이며 소수점은 사용하지 않습니다.
- `createdAt`, `canceledAt`, `timestamp`는 ISO-8601 문자열입니다.
- nullable 필드는 값이 없을 때 `null`로 응답할 수 있습니다.
- `/api/**` 요청 Body는 10KB 이하여야 합니다.
- API 기본 URL은 `http://localhost:8081`입니다.

## Enum

### AccountStatus

| 값 | 설명 |
| --- | --- |
| `ACTIVE` | 정상 계좌 |
| `CANCELED` | 해지 계좌 |

### TransactionType

| 값 | 설명 |
| --- | --- |
| `WITHDRAWAL` | 계좌 출금 |
| `TRANSFER_OUT` | 이체 출금 |
| `TRANSFER_IN` | 이체 입금 |

## IssueTokenRequest

```json
{
  "ci": "CI-20260501-0001",
  "userName": "홍길동",
  "phoneNumber": "01012345678"
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `ci` | string | Y | blank 불가 | 사용자 연계정보 |
| `userName` | string | N | - | 사용자명 |
| `phoneNumber` | string | N | - | 휴대폰번호 |

## IssueTokenResponse

```json
{
  "accessToken": "2a73fb5a-b54d-4bb4-8e7e-97b2d8475d9b",
  "tokenType": "Bearer",
  "expiresIn": 2592000
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | string | API 인가에 사용할 토큰 |
| `tokenType` | string | 항상 `Bearer` |
| `expiresIn` | number | 만료까지 남은 초. 30일은 `2592000` |

## RegisterAccountRequest

```json
{
  "bankCode": "088",
  "productName": "SurePay 입출금통장",
  "initialBalance": 100000
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `bankCode` | string | Y | blank 불가 | 은행사 코드 |
| `productName` | string | Y | blank 불가 | 계좌상품명 |
| `initialBalance` | number | Y | 0 이상 | 초기 잔액 |

## AccountResponse

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

| 필드 | 타입 | nullable | 설명 |
| --- | --- | --- | --- |
| `accountNumber` | string | N | 계좌번호. UUID 기반 랜덤 문자열 |
| `bankCode` | string | N | 은행사 코드 |
| `productName` | string | N | 계좌상품명 |
| `balance` | number | N | 현재 잔액 |
| `status` | string | N | `ACTIVE`, `CANCELED` |
| `createdAt` | string | N | 생성 시각 |
| `canceledAt` | string | Y | 해지 시각 |

## WithdrawRequest

```json
{
  "amount": 10000,
  "memo": "ATM 출금"
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `amount` | number | Y | 1 이상 | 출금 금액 |
| `memo` | string | N | - | 거래 메모 |

## TransferRequest

```json
{
  "fromAccountNumber": "f2b1c7a9e6d24c569f61d5d884bf55f2",
  "toAccountNumber": "b725eacdfb9b480e9ff34d5c1d872f20",
  "amount": 50000,
  "memo": "점심 정산"
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `fromAccountNumber` | string | Y | blank 불가 | 출금 계좌번호 |
| `toAccountNumber` | string | Y | blank 불가 | 입금 계좌번호 |
| `amount` | number | Y | 1 이상 | 이체 금액 |
| `memo` | string | N | - | 거래 메모 |

## TransferResponse

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `fromAccountNumber` | string | 출금 계좌번호 |
| `toAccountNumber` | string | 입금 계좌번호 |
| `amount` | number | 이체 금액 |
| `fromBalance` | number | 출금 후 출금 계좌 잔액 |
| `toBalance` | number | 입금 후 입금 계좌 잔액 |
| `debitHistory` | object | 출금 계좌의 이체 출금 거래내역 |
| `creditHistory` | object | 입금 계좌의 이체 입금 거래내역 |

## TransactionHistoryResponse

```json
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
```

| 필드 | 타입 | nullable | 설명 |
| --- | --- | --- | --- |
| `transactionId` | string | N | 거래 ID |
| `accountNumber` | string | N | 거래 주체 계좌번호 |
| `type` | string | N | 거래 유형 |
| `amount` | number | N | 거래 금액 |
| `counterpartyAccountNumber` | string | Y | 상대 계좌번호 |
| `counterpartyBankCode` | string | Y | 상대 은행코드 |
| `memo` | string | Y | 거래 메모 |
| `createdAt` | string | N | 거래 생성 시각 |

## ErrorResponse

```json
{
  "code": "ACCOUNT_BUSINESS_ERROR",
  "message": "Insufficient balance: f2b1c7a9e6d24c569f61d5d884bf55f2",
  "timestamp": "2026-05-01T01:45:00.000Z"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `code` | string | 오류 코드 |
| `message` | string | 오류 메시지 |
| `timestamp` | string | 오류 발생 시각 |

## 오류 코드

| 코드 | 설명 |
| --- | --- |
| `ACCOUNT_NOT_FOUND` | 계좌가 존재하지 않음 |
| `ACCOUNT_BUSINESS_ERROR` | 잔액 부족, 해지 계좌, 금액 오류 등 |
| `TRANSFER_BUSINESS_ERROR` | 동일 계좌 이체 등 이체 도메인 오류 |

Interceptor가 직접 반환하는 `401`, `403`, `409`, `413`, `500` 오류는 Servlet container 기본 오류 형식으로 내려갈 수 있습니다.
