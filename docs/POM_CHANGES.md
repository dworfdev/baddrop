# Build changes (pom.xml was not provided, so these are instructions, not a patch)

1. **Add** (version is managed by the Spring Boot BOM; this is only the crypto module, NOT the Spring Security starter,
   so no default login page / filter chain is introduced):
   ```xml
   <dependency>
     <groupId>org.springframework.security</groupId>
     <artifactId>spring-security-crypto</artifactId>
   </dependency>
   ```
2. Tests need `spring-boot-starter-test` (already there if the old tests ran). `@ParameterizedTest` needs
   `junit-jupiter-params`, which that starter includes.
3. **Delete the two old test files** (`BankrollEngineTests`, `SlotsDemoApplicationTests`): they call the removed `UserService`
   and the removed password-carrying endpoints and will not compile. Their coverage is replaced by the files under `src/test`.
4. Run: `mvn test`. By category (JUnit tags): `mvn test -Dgroups=unit`, `-Dgroups=security`, `-Dgroups=concurrency`,
   `-Dgroups=invariant`, `-Dgroups=bankroll`, `-Dgroups=integration`.
5. The integration tests boot the app on a random port and use whatever DB your `test` profile configures.
   They do not ship an `application-test.properties` on purpose (it would shadow yours); all test-only settings are
   injected from `IntegrationTestBase`.
