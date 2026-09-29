package fixture

interface Connector {
    @Deprecated("Removed in V7. Use upload instead. See #1234")
    fun oldUpload(path: String)
}
