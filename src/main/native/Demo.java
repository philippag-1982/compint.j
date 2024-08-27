import philippag.lib.common.math.compint.Int9N;

public class Demo {

    public static void main(String[] arg) {
        var a = Int9N.fromString("5".repeat(1000));
        var b = Int9N.fromInt(33);
        var c = a.multiply(b);
        System.out.printf("%s * %s = %s\n", a, b, c);
    }
}
