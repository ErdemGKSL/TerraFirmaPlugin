// In-server self-test plugin (M1-TEST-01). Runs under run-paper in CI.
plugins { id("tfp.paper-conventions") }
dependencies { compileOnly(project(":paper")) }
