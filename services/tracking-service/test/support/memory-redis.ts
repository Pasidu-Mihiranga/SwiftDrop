/** In-memory stand-in for the Redis commands the socket ticket store uses (SET ... EX and GETDEL). */
export function memoryRedis() {
  const values = new Map<string, string>();
  return {
    values,
    set: jest.fn(async (key: string, value: string) => {
      values.set(key, value);
      return "OK";
    }),
    getdel: jest.fn(async (key: string) => {
      const value = values.get(key) ?? null;
      values.delete(key);
      return value;
    }),
  };
}
