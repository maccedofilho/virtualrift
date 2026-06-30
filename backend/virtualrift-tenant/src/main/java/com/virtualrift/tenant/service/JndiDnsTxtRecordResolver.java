package com.virtualrift.tenant.service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

final class JndiDnsTxtRecordResolver implements DnsTxtRecordResolver {

    @Override
    public List<String> resolve(String recordName) throws IOException {
        DirContext context = null;
        try {
            Hashtable<String, String> environment = new Hashtable<>();
            environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            context = new InitialDirContext(environment);
            Attributes attributes = context.getAttributes(recordName, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");
            if (txt == null) {
                return List.of();
            }

            List<String> values = new ArrayList<>();
            NamingEnumeration<?> enumeration = txt.getAll();
            while (enumeration.hasMore()) {
                Object value = enumeration.next();
                if (value != null) {
                    values.add(value.toString());
                }
            }
            return values;
        } catch (NamingException exception) {
            throw new IOException("DNS TXT lookup failed", exception);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }
}
