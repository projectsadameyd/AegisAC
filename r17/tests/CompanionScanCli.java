import dev.aegisac.companion.*;
import java.nio.file.*;
import java.util.*;

/** Local QA bridge: invokes the exact companion scanner without executing inspected mods. */
public final class CompanionScanCli {
    public static void main(String[] args)throws Exception {
        var scan=new LoadedCodeScanner(System.nanoTime()+8_000_000_000L,()->false).scan(List.of(Path.of(args[0])));
        var report=new AdmissionProtocol.Report(Base64.getDecoder().decode(args[1]),AdmissionProtocol.BUILD,AdmissionProtocol.POLICY,scan.outcome(),scan.roots(),scan.classes(),scan.millis(),scan.findings());
        System.out.println("{\"outcome\":\""+scan.outcome()+"\",\"roots\":"+scan.roots()+",\"classes\":"+scan.classes()+",\"millis\":"+scan.millis()+",\"report\":\""+Base64.getEncoder().encodeToString(AdmissionProtocol.encode(report))+"\"}");
    }
}
