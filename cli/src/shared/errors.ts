export class CliError extends Error {
  constructor(
    message: string,
    readonly exitCode: number,
    readonly details: Record<string, unknown> = {},
    readonly httpStatus?: number
  ) {
    super(message)
    this.name = 'CliError'
  }
}
