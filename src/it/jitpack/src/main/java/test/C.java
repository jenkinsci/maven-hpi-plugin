package test;

import jenkins.scm.api.metadata.ObjectMetadataAction;

public class C {
    ObjectMetadataAction action() {
        return new ObjectMetadataAction("Test", null, null);
    }
}
