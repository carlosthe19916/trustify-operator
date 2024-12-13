package org.trustify.operator.cdrs.v2alpha1.server.configmap;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import java.util.List;

@CheckedTemplate
public class AuthTemplate {

    public record Client(String serverUrl, String clientId) {
    }

    public record Data(List<Client> clients) {
    }

    /**
     * Searches for a file with the name of the method of this function into "resources/templates
     *
     * @param data Should Match the fields in the template file
     * @return a Template instance
     */
    public static native TemplateInstance auth(Data data);

}