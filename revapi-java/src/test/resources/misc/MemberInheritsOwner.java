public interface MemberInheritsOwner {
    void method();
    interface Member1 extends MemberInheritsOwner {
    }
    interface Member2 extends MemberInheritsOwner {
    }
}
