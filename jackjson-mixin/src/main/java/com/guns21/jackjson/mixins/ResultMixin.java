package com.guns21.jackjson.mixins;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by jliu on 16/7/13.
 */
@JsonIgnoreProperties({"page", "pageSize", "totals"})
public interface ResultMixin {
}
