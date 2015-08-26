/*
 * Copyright 2007-2107 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ymate.platform.webmvc.support;

import com.alibaba.fastjson.JSON;
import net.ymate.platform.core.util.ClassUtils;
import net.ymate.platform.validation.ValidateResult;
import net.ymate.platform.validation.Validations;
import net.ymate.platform.webmvc.IRequestProcessor;
import net.ymate.platform.webmvc.IWebMvc;
import net.ymate.platform.webmvc.RequestMeta;
import net.ymate.platform.webmvc.base.Type;
import net.ymate.platform.webmvc.view.IView;
import net.ymate.platform.webmvc.view.impl.*;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * WebMVC请求执行器
 *
 * @author 刘镇 (suninformation@163.com) on 2012-12-14 下午4:30:27
 * @version 1.0
 */
public class RequestExecutor {

    private IWebMvc __owner;

    private RequestMeta __requestMeta;

    private IRequestProcessor __requestProcessor;

    public static RequestExecutor bind(IWebMvc owner, RequestMeta requestMeta) {
        return new RequestExecutor(owner, requestMeta);
    }

    private RequestExecutor(IWebMvc owner, RequestMeta requestMeta) {
        __owner = owner;
        __requestMeta = requestMeta;
        if (requestMeta.getProcessor() != null) {
            __requestProcessor = ClassUtils.impl(requestMeta.getProcessor(), IRequestProcessor.class);
        }
        if (__requestProcessor == null) {
            __requestProcessor = __owner.getModuleCfg().getRequestProcessor();
        }
    }

    public IView execute() throws Exception {
        String[] _methodParamNames = ClassUtils.getMethodParamNames(__requestMeta.getMethod());
        Map<String, Object> _paramValues = __requestProcessor.processRequestParams(__owner, __requestMeta, _methodParamNames);
        Map<String, ValidateResult> _resultMap = new HashMap<String, ValidateResult>();
        if (!__requestMeta.isSingleton()) {
            _resultMap = Validations.get(__owner.getOwner()).validate(__requestMeta.getTargetClass(), _paramValues);
        }
        if (_methodParamNames.length > 0) {
            _resultMap.putAll(Validations.get(__owner.getOwner()).validate(__requestMeta.getTargetClass(), __requestMeta.getMethod(), _paramValues));
        }
        if (!_resultMap.isEmpty()) {
            IView _validationView = null;
            if (__owner.getModuleCfg().getErrorProcessor() != null) {
                _validationView = __owner.getModuleCfg().getErrorProcessor().onValidation(__owner, _resultMap);
            }
            if (_validationView == null) {
                throw new IllegalArgumentException(JSON.toJSONString(_resultMap.values()));
            } else {
                return _validationView;
            }
        }
        Object _targetObj = __owner.getOwner().getBean(__requestMeta.getTargetClass());
        if (!__requestMeta.isSingleton()) {
            ClassUtils.wrapper(_targetObj).fromMap(_paramValues);
        }
        if (_methodParamNames.length > 0) {
            Object[] _mParamValues = new Object[_methodParamNames.length];
            for (int _idx = 0; _idx < _methodParamNames.length; _idx++) {
                _mParamValues[_idx] = _paramValues.get(_methodParamNames[_idx]);
            }
            return __doProcessResultToView(__requestMeta.getMethod().invoke(_targetObj, _mParamValues));
        } else {
            return __doProcessResultToView(__requestMeta.getMethod().invoke(_targetObj));
        }
    }

    protected IView __doProcessResultToView(Object result) throws Exception {
        IView _view = null;
        if (result == null) {
            _view = JspView.bind(__owner);
        } else if (result instanceof IView) {
            _view = (IView) result;
        } else if (result instanceof String) {
            String[] _parts = StringUtils.split((String) result, ":");
            if (ArrayUtils.isNotEmpty(_parts) && _parts.length > 1) {
                switch (Type.View.valueOf(_parts[0].toUpperCase())) {
                    case BINARY:
                        _view = BinaryView.bind(new File(_parts[1])).useAttachment(_parts.length >= 3 ? _parts[2] : null);
                        break;
                    case FORWARD:
                        _view = ForwardView.bind(_parts[1]);
                        break;
                    case FREEMARKER:
                        _view = FreemarkerView.bind(__owner, _parts[1]);
                        break;
                    case HTML:
                        _view = HtmlView.bind(__owner, _parts[1]);
                        break;
                    case HTTP_STATES:
                        _view = HttpStatusView.bind(Integer.parseInt(_parts[1]), _parts.length >= 3 ? _parts[2] : null);
                        break;
                    case JSON:
                        _view = JsonView.bind(_parts[1]);
                        break;
                    case JSP:
                        _view = JspView.bind(__owner, _parts[1]);
                        break;
                    case NULL:
                        _view = NullView.bind();
                        break;
                    case REDIRECT:
                        _view = RedirectView.bind(_parts[1]);
                        break;
                    case TEXT:
                        _view = TextView.bind(_parts[1]);
                }
            } else {
                _view = HtmlView.bind((String) result);
            }
        }
        return _view;
    }
}
