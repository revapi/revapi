public class NonPublic {
    static class PackagePrivate {
        public static class Public {
            //this class should not be in the API, because it is enclosed in a package private class
        }
    }
}
