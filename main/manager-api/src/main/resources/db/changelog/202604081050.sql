update `sys_params`
set `param_value` = 'wss://dkyyznecfvae.sealoshzh.site/xiaozhi/v1/'
where `param_code` = 'server.websocket'
  and (`param_value` is null or `param_value` = '' or `param_value` = 'null' or `param_value` = 'ws://xiaozhi.server.com:8000/xiaozhi/v1/');

update `sys_params`
set `param_value` = 'https://dkyyznecfvae.sealoshzh.site/xiaozhi/ota/'
where `param_code` = 'server.ota'
  and (`param_value` is null or `param_value` = '' or `param_value` = 'null');

update `sys_params`
set `param_value` = 'https://dkyyznecfvae.sealoshzh.site'
where `param_code` = 'server.fronted_url'
  and (`param_value` is null or `param_value` = '' or `param_value` = 'http://xiaozhi.server.com');
