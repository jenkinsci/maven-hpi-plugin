import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Sample {@link AbstractStepImpl}.
 *
 * <p>
 * Provides a "helloWorld(String)" step in Pipeline that invokes
 * {@link HelloWorldBuilder}.
 *
 * <p>
 * @author Andrew Bayer
 */
public class HelloWorldStep extends AbstractStepImpl {
    private final String name;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    // If you have multiple fields, it's recommended to use setters with "@DataBoundSetter" instead of putting
    // everything in the constructor.
    @DataBoundConstructor
    public HelloWorldStep(String name) {
        this.name = name;
    }

    /**
     * Corresponds to {@link HelloWorldBuilder#getName()}
     * @return the name configured.
     */
    public String getName() {
        return name;
    }

    // The optional on "@Extension" means this won't be loaded unless the optional dependency, workflow-step-api, is
    // installed, so this won't fail if you don't have Pipeline installed.
    @Extension(optional=true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        /**
         * The function name is what's used in Pipeline code - i.e., 'helloWorld "Steve"'
         *
         * @return the function name
         */
        @Override
        public String getFunctionName() {
            return "helloWorld";
        }

        /**
         * This human readable name is used in the snippet generator screen.
         */
        @Override
        public String getDisplayName() {
            return "Say hello to the world";
        }
    }

    // The actual step execution is handled in this class.
    public static class Execution extends AbstractSynchronousStepExecution<Void> {

        /**
         * The step and its configuration is available to the execution through this variable.
         */
        @Inject
        private transient HelloWorldStep step;

        // The StepContextParameters must be available in the context where this step is executed. If they are missing,
        // the step will throw an exception.
        // These particular StepContextParameters are necessary in order to run a Builder, and mean that this step can
        // only be invoked within a "node { ... }" block in Pipeline.
        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient Run<?,?> run;

        @StepContextParameter
        private transient FilePath ws;

        /**
         * Run when the helloWorld Pipeline step is invoked.
         *
         * @return void since there is no actual return value.
         * @throws Exception
         */
        @Override
        protected Void run() throws Exception {
            // Instantiate the HelloWorldBuilder using the value configured in the step.
            HelloWorldBuilder builder = new HelloWorldBuilder(step.getName());

            // Actually invoke the builder, using the StepContextParameters we required above.
            builder.perform(run, ws, launcher, listener);
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

}
