// Jest setup shared by the Node services' integration tests.
//
// Testcontainers hands out "localhost" addresses. On Windows that name may resolve to ::1 first, and
// Docker Desktop's IPv6 port forwarding can reset those connections (MongoNetworkError: read
// ECONNRESET), so the tests failed although the containers were healthy. Use IPv4 there unless the
// caller chose a host. Linux CI is unchanged.
if (process.platform === "win32" && !process.env.TESTCONTAINERS_HOST_OVERRIDE) {
  process.env.TESTCONTAINERS_HOST_OVERRIDE = "127.0.0.1";
}
