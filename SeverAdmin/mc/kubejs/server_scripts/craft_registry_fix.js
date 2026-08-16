// CraftingDead 注册表诊断 v8.0
// - 去掉 Java.type("java.lang.Class") 这种 KubeJS 6.x 不支持的调用
// - 降级：用 Java.loadClass 加载类本身，如果还想枚举字段，在 Java 层
//   通过 getClass().getSuperclass().getMethod("getDeclaredFields") 访问 ——
//   但为了兼容，这里放弃复杂反射，只判断类和几个常用注册表规模

(function() {
  var LOG = '[CraftFix v8.0]';

  function log(msg) {
    try { console.log(LOG + ' ' + msg); } catch (e) {}
  }

  log('加载诊断工具（KubeJS 6.x 兼容版）...');

  function checkRegistry(label) {
    try {
      var gCls = Java.loadClass('com.craftingdead.core.world.item.gun.GunConfigurations');
      try {
        // Java.loadClass 在 KubeJS 6.x 返回 JavaClass 包装；String 化看一下即可
        log(label + ' ✅ GunConfigurations 类已加载: ' + String(gCls));
      } catch (e1) {
        log(label + ' ✅ GunConfigurations 类已加载');
      }

      // 简单枚举几个常见注册表 supplier 字段：REGISTRY / registry / INSTANCE
      var candidateFields = ['REGISTRY', 'registry', 'INSTANCE', 'DIRECT_CODEC', 'CODEC'];
      var foundEntries = 0;
      for (var fi = 0; fi < candidateFields.length; fi++) {
        var fname = candidateFields[fi];
        try {
          var f;
          try { f = gCls.getClass().getSuperclass().getDeclaredField(fname); } catch (e0) {
            try { f = gCls.getClass().getDeclaredField(fname); } catch (e00) { continue; }
          }
          f.setAccessible(true);
          var val = f.get(null);
          var desc = 'null';
          if (val !== null) {
            try { desc = String(val.getClass()); } catch (e01) { desc = String(val); }
            try {
              var values = val.getValues();
              if (values !== null) {
                var sz = values.size();
                desc += ' (entries=' + sz + ')';
                if (sz > 0) foundEntries = sz;
              }
            } catch (e02) {}
          }
          log('  ' + fname + ': ' + desc);
        } catch (e2) {}
      }

      if (foundEntries > 0) {
        log('✅ ' + label + ' gun_configuration entries=' + foundEntries);
      } else {
        log('⚠️  ' + label + ' gun_configuration 未找到条目（可能 Missing registry 触发）');
      }
    } catch (e) {
      var msg = 'unknown';
      try { msg = String(e.message || e); } catch (e4) {}
      log('❌ ' + label + ' 诊断失败: ' + msg);
    }
  }

  ServerEvents.loaded(function(event) {
    log('======== CraftFix v8.0 诊断 (Server loaded) ========');
    checkRegistry('(loaded)');
  });

  PlayerEvents.loggedIn(function(event) {
    try {
      var player = event.player;
      if (!player) return;
      log('玩家 ' + player.getName().getString() + ' 登录');
      checkRegistry('(player login)');
    } catch (e) {}
  });
})();
