update `sys_params`
set `param_value` = 'wss://dkyyznecfvae.sealoshzh.site/xiaozhi/v1/'
where `param_code` = 'server.websocket'
  and `param_value` in (
    'ws://xiaozhi.server.com:8000/xiaozhi/v1/',
    'wss://api.tenclass.net/xiaozhi/v1/',
    'wss://api.tenclass.net/xiaozhi/v1',
    'wss://2662r3426b.vicp.fun/xiaozhi/v1/',
    'wss://2662r3426b.vicp.fun/xiaozhi/v1'
  );

update `sys_params`
set `param_value` = 'https://dkyyznecfvae.sealoshzh.site/xiaozhi/ota/'
where `param_code` = 'server.ota'
  and `param_value` in (
    'https://api.tenclass.net/xiaozhi/ota/',
    'https://api.tenclass.net/xiaozhi/ota',
    'https://2662r3426b.vicp.fun/xiaozhi/ota/',
    'https://2662r3426b.vicp.fun/xiaozhi/ota'
  );

update `sys_params`
set `param_value` = 'https://dkyyznecfvae.sealoshzh.site'
where `param_code` = 'server.fronted_url'
  and `param_value` in (
    'http://xiaozhi.server.com',
    'http://xiaozhi.server.com/',
    'https://xiaozhi.me',
    'https://xiaozhi.me/',
    'https://2662r3426b.vicp.fun',
    'https://2662r3426b.vicp.fun/'
  );
