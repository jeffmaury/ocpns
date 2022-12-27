package io.ocpns;

import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.ConfigBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.fabric8.openshift.client.OpenShiftClient;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;

@CommandLine.Command
public class Main implements Runnable {

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Get this help message")
    boolean usageHelpRequested;

    @CommandLine.Parameters(index = "0", description = "The namespace to set", arity = "0..1")
    String name;

    @Inject
    CommandLine.IFactory factory;

    public Main() {
    }

    @Override
    public void run() {
        if (usageHelpRequested) {
            try {
                factory.create(CommandLine.class).printVersionHelp(System.out);
            } catch (Exception e) {
            }
        } else if (name != null) {
            setNamespace(name);
        } else {
            listNamespaces();
        }
    }

    private void listNamespaces() {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            Config config = getConfig(new File(io.fabric8.kubernetes.client.Config.getKubeconfigFilename()));
            if (client.isAdaptable(OpenShiftClient.class)) {
                client.adapt(OpenShiftClient.class).projects().list().getItems().forEach(p -> printNamespace(p.getMetadata().getName(), config));

            } else {
                client.namespaces().list().getItems().forEach(n -> printNamespace(n.getMetadata().getName(), config));
            }
        } catch (KubernetesClientException e) {
            System.err.println(ansi().fg(RED).a("Error contacting the cluster " + e.getLocalizedMessage()).reset());
        } catch (IOException e) {
            System.err.println(ansi().fg(RED).a("Error accessing kubeconfig file " + e.getLocalizedMessage()).reset());
        }
    }

    private void printNamespace(String name, Config config) {
        NamedContext ctx = KubeConfigUtils.getCurrentContext(config);
        if (ctx != null && name.equals(ctx.getContext().getNamespace())) {
            System.out.println(ansi().fg(GREEN).bold().a(name).reset());

        } else {
            System.out.println(name);
        }
    }

    private Config getConfig(File f) throws IOException {
        Config config;
        if (f.exists()) {
            config = KubeConfigUtils.parseConfig(f);
        } else {
            config = new ConfigBuilder().build();
        }
        return config;
    }

    private void setNamespace(String namespace) {
        try {
            HasMetadata ns = getNamespace(namespace);
            if (ns != null) {
                try {
                    File f = new File(io.fabric8.kubernetes.client.Config.getKubeconfigFilename());
                    Config config = getConfig(f);
                    NamedContext currentContext = KubeConfigUtils.getCurrentContext(config);
                    if (currentContext != null) {
                        currentContext.getContext().setNamespace(namespace);
                        KubeConfigUtils.persistKubeConfigIntoFile(config, f.getAbsolutePath());
                        System.out.println("Current namespace is '" + namespace + "'");
                    } else {
                        System.err.println(ansi().fg(RED).a("No current context").reset());
                    }
                } catch (IOException e) {
                    System.err.println(ansi().fg(RED).a("Error accessing kubeconfig file " + e.getLocalizedMessage()).reset());
                }
            } else {
                System.err.println(ansi().fg(RED).a("no namespace exists with name '" + namespace + "'").reset());
            }
        } catch (KubernetesClientException e) {
            System.err.println(ansi().fg(RED).a("Error contacting the cluster " + e.getLocalizedMessage()).reset());
        }

    }

    private HasMetadata getNamespace(String namespace) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            if (client.isAdaptable(OpenShiftClient.class)) {
                return client.adapt(OpenShiftClient.class).projects().withName(namespace).get();

            } else {
                return client.namespaces().withName(namespace).get();
            }
        }
    }
}