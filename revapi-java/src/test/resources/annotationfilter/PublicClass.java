package annotationfilter;

@Public
public class PublicClass {
    public int f;

    public void m() {

    }

    @NonPublic(since = "2.0")
    public void implDetail() {

    }

    public class PublicInnerClass {

    }

    @NonPublic
    public class NonPublicInnerClass {

    }
}
