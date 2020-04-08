(function () {
  // 在window对象上挂载全局对象 --- JSBridge对象
  var JSBridge;

  // 为JSBridge定义一个协议名称，用来构建相应的url
  var CUSTOM_PROTOCOL_SCHEME = 'CustomJSBridge';

  //最外层的api名称
  var API_Name = 'namespace_bridge';


  // 创建iframe，用来触发url scheme
  var messagingIframe = document.createElement('iframe');
  messagingIframe.style.display = 'none';
  document.documentElement.appendChild(messagingIframe);
  messagingIframe.src = CUSTOM_PROTOCOL_SCHEME + '://' + API_Name;


  // 定义的回调函数集合,在原生调用完对应的方法后,会执行对应的回调函数id
  var responseCallbacks = {};

  // 唯一id,用来标识responseCallbacks里面的回调函数
  var uniqueId = 1;

  // 本地注册的方法集合, 以供原生调用
  var messageHandlers = {};


  // 定义一个工具类
  var Util = {
    // 用来生成H5回调函数的id
    genCallbackId: function() {
      //如果无法解析端口,可以换为Math.floor(Math.random() * (1 << 30));
      // return 'cb_' + (uniqueId++) + '_' + new Date().getTime();
      return (uniqueId++)
    },

    // 生成最终的url， android中由于原生不能获取JS函数的返回值,所以得通过协议传输
    genUri: function(message) {
      var uri = CUSTOM_PROTOCOL_SCHEME + '://' + API_Name;
      if(message) {
        //回调id作为端口存在
        var callbackId, method, params;
        if(message.callbackId) {
          // 第一种:h5主动调用原生
          callbackId = message.callbackId;
          method = message.handlerName;
          params = message.data;
        } else if(message.responseId) {
          // 第二种:原生调用h5后,h5回调
          callbackId = message.responseId;
          method = message.handlerName;
          params = message.responseData;
        }
        //参数转为字符串
        params = this.stringifyParam(params);
        //uri 补充
        uri += ':' + callbackId + '/' + method + '?' + params;
      }

      return uri;
    },
    // 将参数字符串化
    stringifyParam: function(obj) {
      if(obj && typeof obj === 'object') {
        return JSON.stringify(obj);
      } else {
        return obj || '';
      }
    }
  };


  /**
   * @description JS调用原生方法前,会先send到这里进行处理
   * @param {JSON} message 调用的方法详情,包括方法名,参数
   * @param {Function} responseCallback 调用完方法后的回调
   */
  function _doSend(message, responseCallback) {
    // H5调用原生方法并定义回调函数时，只会将回调函数的id传给原生
    // 只有H5调用原生的时候responseCallback才有值
    if(responseCallback) {
      //取到一个唯一的callbackid
      var callbackId = Util.genCallbackId();
      //回调函数添加到集合中
      responseCallbacks['cb_' + callbackId] = responseCallback;
      // 方法的详情添加回调函数的关键标识
      message['callbackId'] = callbackId;
    }
    //android中兼容处理,将所有的参数一起拼接到url中
    var uri = Util.genUri(message);
    // 利用iframe触发url scheme
    messagingIframe.src = uri;
  }


  // 实际暴露给原生调用的对象
  JSBridge = {
    /**
     * @description 注册本地JS方法通过JSBridge提供给原生调用
     * @param {String} handlerName 方法名
     * @param {Function} handler 回调函数，要求第一个参数是data,第二个参数是callback（这个参数用来让H5触发原生的回调）
     */
    registerHandler: function(handlerName, handler) {
      messageHandlers[handlerName] = handler;
    },
    /**
     * @description H5调用原生开放的方法
     * @param {String} handlerName 方法名
     * @param {JSON} data 参数
     * @param {Function} callback 回调函数
     */
    callHandler: function(handlerName, data, callback) {
      // 参数优化
      if(arguments.length == 3 && typeof data == 'function') {
        callback = data;
        data = null;
      }
      _doSend({
        handlerName: handlerName,
        data: data
      }, callback);
    },
    /**
     * @description 原生调用H5页面注册的方法,或者调用回调方法
     * @param {String} messageJSON 对应的方法的详情,需要手动转为json
     */
    _handleMessageFromNative: function(messageJSON) {
      setTimeout(_doDispatchMessageFromNative);
      /**
       * @description 处理原生过来的方法
       */
      function _doDispatchMessageFromNative() {
        var message;
        try {
            if((typeof messageJSON).toLowerCase() == 'string'){
                message = JSON.parse(messageJSON);
            }else {
                message = messageJSON
            }
        } catch(e) {
          //TODO handle the exception
          console.error("原生调用H5方法出错,传入参数错误");
          return;
        }

        var responseCallback;
        if(message.responseId) {  // 执行H5回调
          //这里规定,原生执行方法完毕后准备通知h5执行回调时,回调函数id是responseId
          responseCallback = responseCallbacks['cb_' + message.responseId];
          if(!responseCallback) {
            return;
          }
          //执行本地的回调函数
          responseCallback(message.responseData);
          delete responseCallbacks['cb_' + message.responseId];
        } else {
          // 原生主动执行h5本地的函数
          // message.callbackId存在的话，其实是原生告诉H5执行完本地注册的方法以后，要通知原生执行相应回调
          if(message.callbackId) {
            // 用来触发原生的回调函数
            responseCallback = function(responseData) {
              //默认是调用EJS api上面的函数
              //然后接下来原生知道scheme被调用后主动获取这个信息
              //所以原生这时候应该会进行判断,判断对于函数是否成功执行,并接收数据
              //这时候通讯完毕(由于h5不会对回调添加回调,所以接下来没有通信了)
              _doSend({
                handlerName: responseData.handlerName,
                responseId: message.callbackId,
                responseData: responseData.data
              });
            };
          }

          //从本地注册的函数中获取
          var handler = messageHandlers[message.handlerName];
          if(!handler) {
            //本地没有注册这个函数
          } else {
            // 执行本地函数,按照要求传入数据和回调
            handler(message.data, responseCallback);
          }
        }
      }
    }

  };
  window.JSBridge = JSBridge

})()
