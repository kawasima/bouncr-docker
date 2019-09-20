import config.SystemCustomizer;
import enkan.Env;
import enkan.system.EnkanSystem;
import enkan.system.command.MetricsCommandRegister;
import enkan.system.command.SqlCommand;
import enkan.system.repl.JShellRepl;
import enkan.system.repl.ReplBoot;
import kotowari.system.KotowariCommandRegister;
import net.unit8.bouncr.api.BouncrApiEnkanSystemFactory;

public class Main {


    public static void main(String[] args) {
        if (Env.getString("enkan.repl", "").equalsIgnoreCase("jshell")) {
            JShellRepl repl = new JShellRepl("net.unit8.bouncr.api.BouncrApiEnkanSystemFactory");
            ReplBoot.start(repl,
                new KotowariCommandRegister(),
                new MetricsCommandRegister(),
                r -> {
                    r.registerCommand("sql", new SqlCommand());
                });
        } else {
            EnkanSystem system = new BouncrApiEnkanSystemFactory().create();
            SystemCustomizer.customize(system);
            Runtime.getRuntime().addShutdownHook(new Thread(system::stop));
            system.start();
        }
    }}
