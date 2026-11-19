// 테스트용 가짜 DurableObject 기반 클래스
export class DurableObject {
  constructor(
    protected ctx: unknown,
    protected env: unknown,
  ) {}
}
