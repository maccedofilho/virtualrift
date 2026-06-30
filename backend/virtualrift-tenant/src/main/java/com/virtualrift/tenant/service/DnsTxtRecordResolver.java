package com.virtualrift.tenant.service;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
interface DnsTxtRecordResolver {

    List<String> resolve(String recordName) throws IOException;
}
