/*
 * Licensed to the University Corporation for Advanced Internet Development, 
 * Inc. (UCAID) under one or more contributor license agreements.  See the 
 * NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The UCAID licenses this file to You under the Apache 
 * License, Version 2.0 (the "License"); you may not use this file except in 
 * compliance with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.internet2.middleware.psp.shibboleth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.opensaml.xml.util.DatatypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.attr.assign.AttributeAssign;
import edu.internet2.middleware.grouper.attr.assign.AttributeAssignType;
import edu.internet2.middleware.grouper.attr.finder.AttributeAssignFinder;
import edu.internet2.middleware.grouper.audit.AuditEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogType;
import edu.internet2.middleware.grouper.misc.GrouperDAOFactory;
import edu.internet2.middleware.grouper.shibboleth.dataConnector.BaseGrouperDataConnector;
import edu.internet2.middleware.grouper.shibboleth.filter.Filter;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.shibboleth.common.attribute.BaseAttribute;
import edu.internet2.middleware.shibboleth.common.attribute.provider.BasicAttribute;
import edu.internet2.middleware.shibboleth.common.attribute.resolver.AttributeResolutionException;
import edu.internet2.middleware.shibboleth.common.attribute.resolver.provider.ShibbolethResolutionContext;
import edu.internet2.middleware.shibboleth.common.attribute.resolver.provider.dataConnector.DataConnector;
import edu.internet2.middleware.subject.Subject;

/** A {@link DataConnector} which returns {@link ChangeLogEntrty} attributes. */
public class ChangeLogDataConnector extends BaseGrouperDataConnector<ChangeLogEntry> {

    /** Logger. */
    private static final Logger LOG = LoggerFactory.getLogger(ChangeLogDataConnector.class);

    /** The pattern for parsing principal names. */
    private static final Pattern pattern = Pattern.compile(CHANGELOG_PRINCIPAL_NAME_PREFIX);

    /** {@inheritDoc} */
    public Map<String, BaseAttribute> resolve(ShibbolethResolutionContext resolutionContext)
            throws AttributeResolutionException {

        String principalName = resolutionContext.getAttributeRequestContext().getPrincipalName();

        LOG.debug("ChangeLog data connector '{}' - Resolve principal '{}'", getId(), principalName);
        LOG.trace("ChangeLog data connector '{}' - Resolve principal '{}' requested attributes {}", new Object[] {
                getId(), principalName, resolutionContext.getAttributeRequestContext().getRequestedAttributesIds(),});

        if (DatatypeHelper.isEmpty(principalName)) {
            LOG.debug("ChangeLog data Connector '{}' - No principal name", getId());
            return Collections.EMPTY_MAP;
        }

        // parse the change log entry sequence number from the principal name
        long sequenceNumber = sequenceNumber(principalName);

        if (sequenceNumber == -1) {
            LOG.debug("ChangeLog data connector '{}' - Principal name '{}' does not match prefix", getId(),
                    principalName);
            return Collections.EMPTY_MAP;
        }

        // query for the change log entry
        ChangeLogEntry changeLogEntry =
                GrouperDAOFactory.getFactory().getChangeLogEntry().findBySequenceNumber(sequenceNumber, false);

        if (changeLogEntry == null) {
            LOG.debug("ChangeLog data connector '{}' - Changelog sequence '{}' not found", getId(), principalName);
            return Collections.EMPTY_MAP;
        }

        // matcher
        Filter<ChangeLogEntry> matcher = getFilter();
        if (!matcher.matches(changeLogEntry)) {
            LOG.debug("ChangeLog data connector '{}' - Ignoring changelog '{}'", getId(), toString(changeLogEntry));
            return Collections.EMPTY_MAP;
        }

        LOG.debug("ChangeLog data connector '{}' - Found change log entry '{}'", getId(), toString(changeLogEntry));
        LOG.trace("ChangeLog data connector '{}' - Found change log entry '{}'", getId(), toStringDeep(changeLogEntry));
        LOG.trace("ChangeLog data connector '{}' - Found change log entry '{}'", getId(), changeLogEntry.toStringDeep());

        Map<String, BaseAttribute> attributes = buildAttributes(changeLogEntry);

        LOG.debug("ChangeLog data connector '{}' - Change log entry {} returning {}", new Object[] {getId(),
                toString(changeLogEntry), attributes,});

        if (LOG.isTraceEnabled()) {
            for (String key : attributes.keySet()) {
                for (Object value : attributes.get(key).getValues()) {
                    LOG.trace("ChangeLog data connector '{}' - Change log entry {} attribute {} : {}", new Object[] {
                            getId(), toString(changeLogEntry), key, value,});
                }
            }
        }

        return attributes;
    }

    /**
     * Return attributes representing the given {@link ChangeLogEntry}.
     * 
     * @param changeLogEntry the changeLogEntry
     * @return the attributes
     */
    protected Map<String, BaseAttribute> buildAttributes(ChangeLogEntry changeLogEntry) {

        // the attributes to be returned
        Map<String, BaseAttribute> attributes = new LinkedHashMap<String, BaseAttribute>();

        // the change log type
        ChangeLogType changeLogType = changeLogEntry.getChangeLogType();

        // return change log attributes
        for (String label : changeLogType.labels()) {
            BasicAttribute<String> attribute = buildAttribute(changeLogEntry, label);
            if (attribute != null) {
                attributes.put(attribute.getId(), attribute);
            }
        }

        // return the change log action name
        BasicAttribute<String> actionNameAttribute = new BasicAttribute<String>("actionName");
        actionNameAttribute.getValues().add(changeLogType.getActionName());
        attributes.put(actionNameAttribute.getId(), actionNameAttribute);

        // return the change log category
        BasicAttribute<String> changeLogCategoryAttribute = new BasicAttribute<String>("changeLogCategory");
        changeLogCategoryAttribute.getValues().add(changeLogType.getChangeLogCategory());
        attributes.put(changeLogCategoryAttribute.getId(), changeLogCategoryAttribute);

        // return the change log sequence number
        BasicAttribute<String> sequenceNumberAttribute = new BasicAttribute<String>("sequenceNumber");
        sequenceNumberAttribute.getValues().add(changeLogEntry.getSequenceNumber().toString());
        attributes.put(sequenceNumberAttribute.getId(), sequenceNumberAttribute);

        // return the change log created on timestamp
        BasicAttribute<String> createdOnAttribute = new BasicAttribute<String>("createdOn");
        createdOnAttribute.getValues().add(changeLogEntry.getCreatedOn().toString());
        attributes.put(createdOnAttribute.getId(), createdOnAttribute);

        // return subject attributes
        if (attributes.containsKey("subjectId") && attributes.containsKey("sourceId")) {
            String sourceId = attributes.get("sourceId").getValues().iterator().next().toString();
            String subjectId = attributes.get("subjectId").getValues().iterator().next().toString();
            attributes.putAll(buildSubjectAttributes(sourceId, subjectId));
        }

        // return attributes from the attribute framework
        attributes.putAll(buildAttributeFrameworkAttributes(changeLogEntry));

        return attributes;
    }

    /**
     * Convert a change log entry attribute to a Shibboleth attribute.
     * 
     * @param changeLogEntry the change log entry
     * @param label the attribute name
     * @return the shibboleth attribute representing the change log attribute
     */
    protected BasicAttribute<String> buildAttribute(ChangeLogEntry changeLogEntry, String label) {

        String value = changeLogEntry.retrieveValueForLabel(label);

        if (value == null) {
            return null;
        }

        BasicAttribute<String> attribute = new BasicAttribute<String>();
        attribute.setId(label);
        attribute.getValues().add(value);
        return attribute;
    }

    /**
     * If the change log entry is an attribute value assignment, return (a) the 'name' of the group or stem that the
     * attribute value was assigned to or (b) the 'memberSubjectId' of the member that the attribute value was assigned
     * to as well as (c) an attribute representing the value assignment. Otherwise, return an empty map.
     * 
     * @param changeLogEntry the change log entry
     * @return (a) the 'name' of the group or stem that the attribute value was assigned to or (b) the 'memberSubjectId'
     *         of the member that the attribute value was assigned to as well as (c) an attribute representing the value
     *         assignment or null
     */
    protected Map<String, BaseAttribute> buildAttributeFrameworkAttributes(ChangeLogEntry changeLogEntry) {

        ChangeLogType changeLogType = changeLogEntry.getChangeLogType();

        // return attributes if this is an attribute assign value change log entry
        if (!changeLogType.getChangeLogCategory().equals("attributeAssignValue")) {
            return Collections.EMPTY_MAP;
        }

        // get the attribute assign id
        String attributeAssignId = changeLogEntry.retrieveValueForLabel("attributeAssignId");
        if (attributeAssignId == null) {
            return Collections.EMPTY_MAP;
        }

        // get the attribute assignment
        AttributeAssign attributeAssign = AttributeAssignFinder.findById(attributeAssignId, false);
        if (attributeAssign == null) {
            return Collections.EMPTY_MAP;
        }

        // the attributes to return
        Map<String, BaseAttribute> attributes = new LinkedHashMap<String, BaseAttribute>();

        // return an attribute with name attributeDefNameName and value
        String attributeDefNameName = changeLogEntry.retrieveValueForLabel("attributeDefNameName");
        String value = changeLogEntry.retrieveValueForLabel("value");
        if (attributeDefNameName != null && value != null) {
            BasicAttribute<String> attribute = new BasicAttribute<String>();
            attribute.setId(attributeDefNameName);
            attribute.getValues().add(value);
            attributes.put(attribute.getId(), attribute);
        }

        // return an attributeAssignType attribute
        AttributeAssignType attributeAssignType = attributeAssign.getAttributeAssignType();
        if (attributeAssignType != null) {
            BasicAttribute<String> attribute = new BasicAttribute<String>();
            attribute.setId("attributeAssignType");
            attribute.getValues().add(attributeAssign.getAttributeAssignType().getName());
            attributes.put(attribute.getId(), attribute);
        }

        // if the attribute was assigned to a group, return the group name
        if (attributeAssign.getAttributeAssignType() == AttributeAssignType.group) {
            Group group = attributeAssign.getOwnerGroup();
            if (group != null) {
                BasicAttribute<String> attribute = new BasicAttribute<String>();
                attribute.setId("name");
                attribute.getValues().add(group.getName());
                attributes.put(attribute.getId(), attribute);
            }
        }

        // if the attribute was assigned to a stem, return the stem name
        if (attributeAssign.getAttributeAssignType() == AttributeAssignType.stem) {
            Stem stem = attributeAssign.getOwnerStem();
            if (stem != null) {
                BasicAttribute<String> attribute = new BasicAttribute<String>();
                attribute.setId("name");
                attribute.getValues().add(stem.getName());
                attributes.put(attribute.getId(), attribute);
            }
        }

        // if the attribute was assigned to a member, return the member subject id
        if (attributeAssign.getAttributeAssignType() == AttributeAssignType.member) {
            Member member = attributeAssign.getOwnerMember();
            if (member != null) {
                BasicAttribute<String> attribute = new BasicAttribute<String>();
                attribute.setId("memberSubjectId");
                attribute.getValues().add(member.getSubjectId());
                attributes.put(attribute.getId(), attribute);
            }
        }

        return attributes;
    }

    /**
     * Return attributes representing the subject with the given source id and subject id.
     * 
     * The subject is found via SubjectFinder.findByIdAndSource();
     * 
     * The subject name is returned via the 'subjectName' attribute. The subject description is returned via the
     * 'subjectDescription' attribute.
     * 
     * Subject attributes returned from subject.getAttributes() are named 'subject' + attribute name, for example,
     * 'subjectdisplayextension'.
     * 
     * @param sourceId the source id
     * @param subjectId the subject id
     * @return attributes representing the subject
     */
    protected Map<String, BaseAttribute> buildSubjectAttributes(String sourceId, String subjectId) {

        // look up subject
        Subject subject = SubjectFinder.findByIdAndSource(subjectId, sourceId, false);
        if (subject == null) {
            return Collections.EMPTY_MAP;
        }

        Map<String, BaseAttribute> attributes = new LinkedHashMap<String, BaseAttribute>();

        String subjectName = subject.getName();
        if (!DatatypeHelper.isEmpty(subjectName)) {
            BasicAttribute<String> attribute = new BasicAttribute<String>();
            attribute.setId("subjectName");
            attribute.getValues().add(subjectName);
            attributes.put(attribute.getId(), attribute);
        }

        String subjectDescription = subject.getDescription();
        if (!DatatypeHelper.isEmpty(subjectDescription)) {
            BasicAttribute<String> attribute = new BasicAttribute<String>();
            attribute.setId("subjectDescription");
            attribute.getValues().add(subjectDescription);
            attributes.put(attribute.getId(), attribute);
        }

        Map<String, Set<String>> attributeMap = subject.getAttributes();
        if (attributeMap != null) {
            for (String attributeName : attributeMap.keySet()) {
                BasicAttribute<String> attribute = new BasicAttribute<String>();
                attribute.setId("subject" + attributeName);
                attribute.getValues().addAll(attributeMap.get(attributeName));
                attributes.put(attribute.getId(), attribute);
            }
        }

        return attributes;
    }

    /** {@inheritDoc} */
    public void validate() throws AttributeResolutionException {

    }

    /**
     * A hack. Return a principal name suitable for processing based on a change log sequence number.
     * 
     * @param changeLogSequenceNumber the change log sequence number
     * @return the prefixed principal name
     */
    public static String principalName(long changeLogSequenceNumber) {
        return CHANGELOG_PRINCIPAL_NAME_PREFIX + Long.toString(changeLogSequenceNumber);
    }

    /**
     * A hack. Get the sequence number from a prefixed principal name. Returns -1 if the principal name does not match
     * the prefix or an error occurred parsing the sequence number.
     * 
     * @param principalName the prefixed principal name
     * @return the sequence number
     */
    public static long sequenceNumber(String principalName) {
        String[] toks = pattern.split(principalName);
        if (toks.length == 2) {
            try {
                return Long.parseLong(toks[1]);
            } catch (NumberFormatException e) {
                LOG.error("ChangeLog data connector - Unable to parse principal name '{}'", principalName, e);
                return -1;
            }
        }
        return -1;
    }

    /**
     * Return a string representing an {@AuditEntry}.
     * 
     * Returned fields include timestamp, category, actionname, and description.
     * 
     * Uses {@ToStringBuilder}.
     * 
     * @param auditEntry the audit entry
     * @return the string representing the audit entry
     */
    public static String toString(AuditEntry auditEntry) {
        ToStringBuilder toStringBuilder = new ToStringBuilder(auditEntry, ToStringStyle.SHORT_PREFIX_STYLE);
        toStringBuilder.append("timestamp", auditEntry.getCreatedOn());
        toStringBuilder.append("category", auditEntry.getAuditType().getAuditCategory());
        toStringBuilder.append("actionname", auditEntry.getAuditType().getActionName());
        toStringBuilder.append("description", auditEntry.getDescription());
        return toStringBuilder.toString();
    }

    /**
     * Return a string representing an {ChangeLogEntry}.
     * 
     * Returned fields include timestamp, category, actionname, and description.
     * 
     * Uses {@ToStringBuilder}.
     * 
     * @param changeLogEntry the change log entry
     * @return the string representing the change log entry
     */
    public static String toString(ChangeLogEntry changeLogEntry) {
        ToStringBuilder toStringBuilder = new ToStringBuilder(changeLogEntry, ToStringStyle.SHORT_PREFIX_STYLE);
        toStringBuilder.append("timestamp", changeLogEntry.getCreatedOn());
        toStringBuilder.append("sequence", changeLogEntry.getSequenceNumber());
        toStringBuilder.append("category", changeLogEntry.getChangeLogType().getChangeLogCategory());
        toStringBuilder.append("actionname", changeLogEntry.getChangeLogType().getActionName());
        toStringBuilder.append("contextId", changeLogEntry.getContextId());
        return toStringBuilder.toString();
    }

    /**
     * Return a string representing an {ChangeLogEntry}.
     * 
     * Returns all labels (attributes).
     * 
     * Uses {@ToStringBuilder}.
     * 
     * @param changeLogEntry the change log entry
     * @return the string representing the entire change log entry
     */
    public static String toStringDeep(ChangeLogEntry changeLogEntry) {
        ToStringBuilder toStringBuilder = new ToStringBuilder(changeLogEntry, ToStringStyle.SHORT_PREFIX_STYLE);
        toStringBuilder.append("timestamp", changeLogEntry.getCreatedOn());
        toStringBuilder.append("sequence", changeLogEntry.getSequenceNumber());
        toStringBuilder.append("category", changeLogEntry.getChangeLogType().getChangeLogCategory());
        toStringBuilder.append("actionname", changeLogEntry.getChangeLogType().getActionName());
        toStringBuilder.append("contextId", changeLogEntry.getContextId());

        ChangeLogType changeLogType = changeLogEntry.getChangeLogType();

        for (String label : changeLogType.labels()) {
            toStringBuilder.append(label, changeLogEntry.retrieveValueForLabel(label));
        }

        return toStringBuilder.toString();
    }

}
