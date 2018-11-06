/*
 * Copyright 2014-2018 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
{
  "type": "object",
  "properties": {
    "transformBlocks": {
        "type": "array",
        "items": {
            "type": "array",
            "items": {
                "type": "string"
            }
        }
    },
    "analyzers": {
        "$ref": "#/definitions/extensionType"
    },
    "filters": {
        "$ref": "#/definitions/extensionType"
    },
    "transforms": {
        "$ref": "#/definitions/extensionType"
    },
    "reporters": {
        "$ref": "#/definitions/extensionType"
    }
  },
  "definitions": {
    "idList": {
        "oneOf": [
            {
                "type": "array",
                "items": {
                    "type": "string"
                }
            },
            {
                "type": "string"
            }
        ]
    },
    "extensionType": {
        "type": "object",
        "properties": {
            "include": {
                "$ref": "#/definitions/idList"
            },
            "exclude": {
                "$ref": "#/definitions/idList"
            }
        }
    }
  }
}