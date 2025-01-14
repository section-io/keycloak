package org.keycloak.broker.saml.mappers;

import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.saml.SAMLEndpoint;
import org.keycloak.broker.saml.SAMLIdentityProviderFactory;
import org.keycloak.broker.provider.IdentityProviderMapper;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.common.util.CollectionUtil;
import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderSyncMode;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.saml.common.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

import static org.keycloak.utils.RegexUtils.valueMatchesRegex;
/**
 * @author Alice Knag
 * @version $Revision: 1 $
 */
public class AdvancedUserAttributeMapper extends AbstractIdentityProviderMapper implements SamlMetadataDescriptorUpdater {
    public static final String[] COMPATIBLE_PROVIDERS = {SAMLIdentityProviderFactory.PROVIDER_ID};
    private static final Logger log = Logger.getLogger(AdvancedUserAttributeMapper.class);
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    public static final String ATTRIBUTE_NAME = "attribute.name";
    public static final String ATTRIBUTE_REGEX_MATCH = "attribute.regex.match";
    public static final String ATTRIBUTE_FRIENDLY_NAME = "attribute.friendly.name";
    public static final String USER_ATTRIBUTE = "user.attribute";
    private static final String EMAIL = "email";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final Set<IdentityProviderSyncMode> IDENTITY_PROVIDER_SYNC_MODES = new HashSet<>(Arrays.asList(IdentityProviderSyncMode.values()));

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(ATTRIBUTE_NAME);
        property.setLabel("Attribute Name");
        property.setHelpText("Name of attribute to search for in assertion.  You can leave this blank and specify a friendly name instead.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);
        property = new ProviderConfigProperty();
        property.setName(ATTRIBUTE_FRIENDLY_NAME);
        property.setLabel("Friendly Name");
        property.setHelpText("Friendly name of attribute to search for in assertion.  You can leave this blank and specify a name instead.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);
        property = new ProviderConfigProperty();
        property.setName(USER_ATTRIBUTE);
        property.setLabel("User Attribute Name");
        property.setHelpText("User attribute name to store saml attribute.  Use email, lastName, and firstName to map to those predefined user properties.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);
        property = new ProviderConfigProperty();
        property.setName(ATTRIBUTE_REGEX_MATCH);
        property.setLabel( "Attribute Value Regex");
        property.setHelpText("The regex to match the attribute value against. For example: ^.*@example\\.com$");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);
    }

    public static final String PROVIDER_ID = "saml-advanced-user-attribute-idp-mapper";

    @Override
    public boolean supportsSyncMode(IdentityProviderSyncMode syncMode) {
        return IDENTITY_PROVIDER_SYNC_MODES.contains(syncMode);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String[] getCompatibleProviders() {
        return COMPATIBLE_PROVIDERS;
    }

    @Override
    public String getDisplayCategory() {
        return "Advanced Attribute Importer";
    }

    @Override
    public String getDisplayType() {
        return "Advanced Attribute Importer";
    }

    @Override
    public void preprocessFederatedIdentity(KeycloakSession session, RealmModel realm, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        String attribute = mapperModel.getConfig().get(USER_ATTRIBUTE);
        if (StringUtil.isNullOrEmpty(attribute)) {
            return;
        }
        String attributeName = getAttributeNameFromMapperModel(mapperModel);
        String attributeRegex = getRegexFromMapperModel(mapperModel);

        List<String> attributeValuesInContext = findAttributeValuesInContext(attributeName, context);
        if (!attributeValuesInContext.isEmpty()) {
            if(valueMatchesRegex(attributeRegex,attributeValuesInContext.get(0))) {
                if (attribute.equalsIgnoreCase(EMAIL)) {
                    setIfNotEmpty(context::setEmail, attributeValuesInContext);
                } else if (attribute.equalsIgnoreCase(FIRST_NAME)) {
                    setIfNotEmpty(context::setFirstName, attributeValuesInContext);
                } else if (attribute.equalsIgnoreCase(LAST_NAME)) {
                    setIfNotEmpty(context::setLastName, attributeValuesInContext);
                } else {
                    context.setUserAttribute(attribute, attributeValuesInContext);
                }
            }else{
            throw new IdentityBrokerException(String.format("Regex didn't match during IDP brokering for attribute: %s, with regex %s", attributeValuesInContext.get(0), attributeRegex));
            }
        }
    }

    private String getAttributeNameFromMapperModel(IdentityProviderMapperModel mapperModel) {
        String attributeName = mapperModel.getConfig().get(ATTRIBUTE_NAME);
        if (attributeName == null) {
            attributeName = mapperModel.getConfig().get(ATTRIBUTE_FRIENDLY_NAME);
        }
        return attributeName;
    }

    private String getRegexFromMapperModel(IdentityProviderMapperModel mapperModel) {
        String attributeName = mapperModel.getConfig().get(ATTRIBUTE_REGEX_MATCH);
        if (attributeName == null) {
            attributeName = "";
        }
        return attributeName;
    }

    private void setIfNotEmpty(Consumer<String> consumer, List<String> values) {
        if (values != null && !values.isEmpty()) {
            consumer.accept(values.get(0));
        }
    }

    private void setIfNotEmptyAndDifferent(Consumer<String> consumer, Supplier<String> currentValueSupplier, List<String> values) {
        if (values != null && !values.isEmpty() && !values.get(0).equals(currentValueSupplier.get())) {
            consumer.accept(values.get(0));
        }
    }

    private Predicate<AttributeStatementType.ASTChoiceType> elementWith(String attributeName) {
        return attributeType -> {
            AttributeType attribute = attributeType.getAttribute();
            return Objects.equals(attribute.getName(), attributeName)
                    || Objects.equals(attribute.getFriendlyName(), attributeName);
        };
    }


    private List<String> findAttributeValuesInContext(String attributeName, BrokeredIdentityContext context) {
        AssertionType assertion = (AssertionType) context.getContextData().get(SAMLEndpoint.SAML_ASSERTION);

        return assertion.getAttributeStatements().stream()
                .flatMap(statement -> statement.getAttributes().stream())
                .filter(elementWith(attributeName))
                .flatMap(attributeType -> attributeType.getAttribute().getAttributeValue().stream())
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        String attribute = mapperModel.getConfig().get(USER_ATTRIBUTE);
        if (StringUtil.isNullOrEmpty(attribute)) {
            return;
        }
        String attributeName = getAttributeNameFromMapperModel(mapperModel);
        String attributeRegex = getRegexFromMapperModel(mapperModel);
        List<String> attributeValuesInContext = findAttributeValuesInContext(attributeName, context);
        if(valueMatchesRegex(attributeRegex,attributeValuesInContext.get(0))) {
            if (attribute.equalsIgnoreCase(EMAIL)) {
                setIfNotEmptyAndDifferent(user::setEmail, user::getEmail, attributeValuesInContext);
            } else if (attribute.equalsIgnoreCase(FIRST_NAME)) {
                setIfNotEmptyAndDifferent(user::setFirstName, user::getFirstName, attributeValuesInContext);
            } else if (attribute.equalsIgnoreCase(LAST_NAME)) {
                setIfNotEmptyAndDifferent(user::setLastName, user::getLastName, attributeValuesInContext);
            } else {
                List<String> currentAttributeValues = user.getAttributes().get(attribute);
                if (attributeValuesInContext == null) {
                    // attribute no longer sent by brokered idp, remove it
                    user.removeAttribute(attribute);
                } else if (currentAttributeValues == null) {
                    // new attribute sent by brokered idp, add it
                    user.setAttribute(attribute, attributeValuesInContext);
                } else if (!CollectionUtil.collectionEquals(attributeValuesInContext, currentAttributeValues)) {
                    // attribute sent by brokered idp has different values as before, update it
                    user.setAttribute(attribute, attributeValuesInContext);
                }
                // attribute already set
            }
        }else{
            if (attributeValuesInContext == null) {
                // attribute no longer sent by brokered idp, remove it
                user.removeAttribute(attribute);
            }

            throw new IdentityBrokerException(String.format("Regex didn't match during IDP brokering for attribute: %s, with regex %s", attributeValuesInContext.get(0), attributeRegex));
        }
    }

    @Override
    public String getHelpText() {
        return "Import declared saml attribute if it exists in assertion into the specified user property or attribute.";
    }

}
