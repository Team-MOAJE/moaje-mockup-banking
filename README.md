# Mock Banking (backing-mockup)

모아제의 실습용 계정계입니다. 전용 Redis에서 실제 계좌 잔액과 거래 결과를 관리하며 Banking에 송금 결과·계좌 스냅샷을 제공합니다.

- [개발 기록과 검증 결과](https://github.com/Team-MOAJE/moaje-infra/blob/main/docs/phase-history-and-retrospective.md)
- [공개 문서와 ADR](https://github.com/Team-MOAJE/moaje-infra/blob/main/docs/README.md)
- [로컬 통합 실행](https://github.com/Team-MOAJE/moaje-infra/blob/main/readMe.md)

실은행 수준의 내구성·운영 보안을 보장하는 서버가 아닙니다. 실제 개인정보나 금융 계좌를 넣지 않고 개발용 합성 데이터로 사용합니다.

## 업무 오류 응답 계약

HTTP 상태와 JSON 본문의 `code`를 함께 확인합니다. 본문은 `code`, `message`, `timestamp`로 구성되며, `message` 문구는 상태 판정에 사용하지 않습니다.

| 상황 | HTTP | code |
|---|---|---|
| 계좌 없음 | 404 | `ACCOUNT_NOT_FOUND` |
| 계좌 해지 | 400 | `ACCOUNT_CANCELED` |
| 잔액 부족 | 400 | `INSUFFICIENT_BALANCE` |
| 업무 계층에서 잘못된 금액 거절 | 400 | `INVALID_ACCOUNT_AMOUNT` |
| 출금·수취 계좌 동일 | 400 | `SAME_ACCOUNT_TRANSFER` |
| 결과 조회에서 거래 기록 없음 | 404 | `TRANSFER_NOT_FOUND` |

- 송금 경로의 위 업무 검사는 잔액 변경 전에 수행합니다. 결과 조회의 `TRANSFER_NOT_FOUND`는 송금 요청의 거절 코드와 구분합니다.
- DTO 검증 오류(빈 필수값·0 이하 금액), 인증 Interceptor의 오류, 내부 500은 위 업무 오류 형식으로 통일하지 않았습니다. 단순히 HTTP 400/404/500만 보고 원거래 실패로 확정하면 안 됩니다.
- 이미 실행된 `clientTransferId`의 중복 요청도 현재 내부 예외를 발생시킵니다. 오류 응답이 원거래 실패를 의미하지 않으므로 결과 조회가 필요합니다.
- Banking의 오류 분류·UNKNOWN 대사 처리는 별도 후속 작업입니다. 이번 변경으로 기존의 포괄 코드 `ACCOUNT_BUSINESS_ERROR`, `TRANSFER_BUSINESS_ERROR`를 구체화했습니다.
- 검증: `TransferErrorResponseContractTest`는 실제 Controller·Advice를 통한 HTTP 상태/코드/메시지를 검사합니다. Redis 원장 실행을 재현하는 테스트는 아닙니다.

