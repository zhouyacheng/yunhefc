/** 只接受后端现签的 http(s) 签名 URL；对象键或空值回落姓名首字占位。 */
export function avatarSrc(url: string | null | undefined): string | null {
  return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : null;
}

/** 取姓名首个字符；按码点切分，避免 emoji 等代理对截断。 */
export function avatarInitial(name: string): string {
  const [first] = Array.from(name.trim());
  return first ?? '?';
}
