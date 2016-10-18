package com.virtusa.gto.insight.nyql.model

import groovy.transform.ToString

import java.util.stream.Collectors

/**
 * @author IWEERARATHNA
 */
@ToString(includes = ["scripts"])
class QScriptList extends QScript {

    List<QScript> scripts

    @Override
    void free() {
        super.free()
        if (scripts != null) {
            scripts.each { it.free() }
            scripts.clear()
        }
    }

    @Override
    public String toString() {
        return "QScriptList{" +
                "scripts=\n" + scripts.stream().map({it.toString()}).collect(Collectors.joining(",\n")) +
                '}';
    }
}
