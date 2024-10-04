/**
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.openbanking.cds.common.enums;

/**
 * Specifies the type of consent duration.
 */
public enum ConsentDurationTypeEnum {

    ONCE_OFF("once-off"),
    ONGOING("ongoing");

    private String value;

    ConsentDurationTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ConsentDurationTypeEnum fromValue(String value) {
        for (ConsentDurationTypeEnum type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}