package fixture;

public class Conforming {

    /**
     * Old way.
     *
     * @deprecated Removed in V7. Use the PUT method
     * instead. See #1234
     */
    @PostMapping("/old")
    @Deprecated(forRemoval = true)
    public void oldEndpoint(String value) {
    }
}
