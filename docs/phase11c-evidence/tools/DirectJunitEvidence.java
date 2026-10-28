import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.nio.file.*;
import java.util.*;
public class DirectJunitEvidence {
  static class Case { Description d; String failure; boolean skipped; Case(Description d) { this.d=d; } }
  static String xml(String s) {return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");}
  public static void main(String[] args) throws Exception {
    List<Case> cases = new ArrayList<>();
    JUnitCore core = new JUnitCore();
    core.addListener(new RunListener() {
      public void testStarted(Description d) {cases.add(new Case(d)); System.out.println("START " + d.getMethodName());}
      public void testFailure(Failure f) { Case c=cases.stream().filter(x->x.d.equals(f.getDescription())).reduce((a,b)->b).orElseGet(()->{Case x=new Case(f.getDescription());cases.add(x);return x;}); c.failure=f.getTrace();System.out.println(f.getTrace()); }
      public void testAssumptionFailure(Failure f) {testFailure(f);}
      public void testIgnored(Description d) {Case c=new Case(d);c.skipped=true;cases.add(c);}
    });
    Result r = core.run(Class.forName(args[0]));
    StringBuilder out=new StringBuilder("<testsuite tests=\""+r.getRunCount()+"\" failures=\""+r.getFailureCount()+"\" skipped=\""+r.getIgnoreCount()+"\">\n");
    for(Case c:cases) {out.append("<testcase classname=\"").append(xml(c.d.getClassName())).append("\" name=\"").append(xml(c.d.getMethodName())).append("\">");if(c.failure!=null)out.append("<failure>").append(xml(c.failure)).append("</failure>");if(c.skipped)out.append("<skipped/>");out.append("</testcase>\n");}
    out.append("</testsuite>\n");Files.writeString(Path.of(args[1]),out.toString());
    System.out.printf("ACTUAL tests=%d failures=%d skipped=%d%n",r.getRunCount(),r.getFailureCount(),r.getIgnoreCount());System.exit(r.wasSuccessful()&&r.getIgnoreCount()==0?0:1);
  }
}
