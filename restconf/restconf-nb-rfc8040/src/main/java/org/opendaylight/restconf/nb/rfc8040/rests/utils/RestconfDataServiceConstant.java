/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Constants for RestconfDataService.
 *
 */
public final class RestconfDataServiceConstant {
    public static final QName NETCONF_BASE_QNAME = SchemaContext.NAME;

    private RestconfDataServiceConstant() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Constants for read data.
     *
     */
    public static final class ReadData {
        // URI parameters
        public static final String CONTENT = "content";
        public static final String DEPTH = "depth";
        public static final String FIELDS = "fields";

        // content values
        public static final String CONFIG = "config";
        public static final String ALL = "all";
        public static final String NONCONFIG = "nonconfig";

        // depth values
        public static final String UNBOUNDED = "unbounded";
        public static final int MIN_DEPTH = 1;
        public static final int MAX_DEPTH = 65535;

        public static final String READ_TYPE_TX = "READ";
        public static final String WITH_DEFAULTS = "with-defaults";
        public static final String REPORT_ALL_DEFAULT_VALUE = "report-all";
        public static final String REPORT_ALL_TAGGED_DEFAULT_VALUE = "report-all-tagged";

        private ReadData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }

    /**
     * Common for PostData and PutData.
     */
    public static final class PostPutQueryParameters {
        public static final String INSERT = "insert";
        public static final String POINT = "point";

        private PostPutQueryParameters() {
            // Hidden on purpose
        }
    }

    /**
     * Constants for data to put.
     *
     */
    public static final class PutData {
        public static final String PUT_TX_TYPE = "PUT";

        private PutData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }

    /**
     * Constants for data to post.
     *
     */
    public static final class PostData {
        public static final String POST_TX_TYPE = "POST";

        private PostData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }

    /**
     * Constants for data to delete.
     *
     */
    public static final class DeleteData {
        public static final String DELETE_TX_TYPE = "DELETE";

        private DeleteData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }

    /**
     * Constants for data to yang patch.
     *
     */
    public static final class PatchData {
        public static final String PATCH_TX_TYPE = "Patch";

        private PatchData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }
}
