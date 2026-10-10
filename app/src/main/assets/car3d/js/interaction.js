console.log('=== Interaction.js 开始加载 ===');

class InteractionController {
  constructor() {
    this.ready = false;
    this.longPressTimer = null;
    this.longPressEndTime = 0;
    this.deltaTheta = Math.PI / 4;
    this.deltaPhi = Math.PI / 8;
    this.setupEventBridge();
    this.waitForMainScript();
  }

  waitForMainScript() {
    const self = this;
    let attempts = 0;
    
    const checkInterval = setInterval(() => {
      attempts++;
      
      if (window.i && window.v) {
        clearInterval(checkInterval);
        this.ready = true;
        console.log('=== InteractionController 已就绪 ===');
        
        if (window.Vector3) {
          console.log('Vector3 已加载');
        }
        
        if (window.i.freeCamera) {
          console.log('自由相机已初始化');
        }
      } else if (attempts > 50) {
        clearInterval(checkInterval);
        console.warn('=== InteractionController 等待超时 ===');
        console.log('window.i:', !!window.i);
        console.log('window.v:', !!window.v);
      }
    }, 200);
  }

  setupEventBridge() {
    const self = this;
    
    window.dispatchInteractionEvent = function(eventType, detail) {
      self.handleInteraction(eventType, detail);
    };
    
    document.addEventListener('stateTableClick', function(e) {
      const detail = e.detail;
      self.handleDivClickData(detail.containerIndex, detail.divIndex, detail.className);
    });
  }

  handleInteraction(eventType, detail) {
    switch(eventType) {
      case 'divClick':
        this.handleDivClickData(detail.containerIndex, detail.divIndex, detail.className);
        break;
      case 'longPress':
        this.handleLongPressData(detail.containerIndex, detail.divIndex, detail.className);
        break;
    }
  }

  handleDivClickData(containerIndex, divIndex, className) {
    this.handleActionByClass(className, containerIndex, divIndex);
    
    this.dispatchCustomEvent('divClick', {
      containerIndex,
      divIndex,
      className
    });
  }

  handleLongPressData(containerIndex, divIndex, className) {
    this.dispatchCustomEvent('divLongPress', {
      containerIndex,
      divIndex,
      className
    });
  }

  handleActionByClass(className, containerIndex, divIndex) {
    if (!window.v) return;

    if (className.includes('Bar')) {
      if (containerIndex === 0) {
        window.v.colorBodyIndex = divIndex;
      } else if (containerIndex === 1) {
        window.v.colorInteriorIndex = divIndex;
      }
    } else if (className.includes('item')) {
      const states = [2, 3, 4];
      if (states[divIndex] !== undefined) {
        window.v.state = states[divIndex];
      }
    }
  }

  dispatchCustomEvent(eventName, detail) {
    const event = new CustomEvent(eventName, {
      detail: detail,
      bubbles: true,
      cancelable: true
    });
    document.dispatchEvent(event);
  }

  getTargetTheta() {
    if (!window.i || !window.i.freeCamera) return 0;
    return window.i.freeCamera._targetTheta || 0;
  }

  getTargetPhi() {
    if (!window.i || !window.i.freeCamera) return Math.PI / 2;
    return window.i.freeCamera._targetPhi || Math.PI / 2;
  }

  getCurrentCameraAngles() {
    if (!window.i || !window.i.freeCamera) {
      return { theta: 0, phi: Math.PI / 2 };
    }
    const camera = window.i.freeCamera;
    return {
      theta: camera._spherical ? camera._spherical.theta : 0,
      phi: camera._spherical ? camera._spherical.phi : Math.PI / 2,
      targetTheta: camera._targetTheta || 0,
      targetPhi: camera._targetPhi || Math.PI / 2
    };
  }

  setTargetTheta(delta) {
    if (!window.i || !window.i.freeCamera) return;
    
    const camera = window.i.freeCamera;
    const currentTarget = camera._targetTheta || 0;
    camera._targetTheta = currentTarget + delta;
    console.log('设置_targetTheta: ' + (currentTarget + delta).toFixed(3) + ' (delta=' + delta.toFixed(3) + ')');
  }

  setTargetPhi(delta) {
    if (!window.i || !window.i.freeCamera) return;
    
    const camera = window.i.freeCamera;
    const currentTarget = camera._targetPhi || Math.PI / 2;
    const newPhi = Math.max(0.5, Math.min(Math.PI / 2, currentTarget + delta));
    camera._targetPhi = newPhi;
    console.log('设置_targetPhi: ' + newPhi.toFixed(3));
  }

  simulateLongPress(duration) {
    const ms = duration || 3000;
    console.log('simulateLongPress 调用, duration:', ms);
    
    if (!window.v) {
      console.error('window.v 未定义，无法触发长按');
      return;
    }
    
    if (this.longPressTimer !== null) {
      clearTimeout(this.longPressTimer);
      this.longPressTimer = null;
      console.log('清除之前的长按计时器');
    }
    
    window.v.presssed = true;
    this.longPressEndTime = Date.now() + ms;
    console.log('触发长按交互，持续', ms, '毫秒');
    
    const self = this;
    this.longPressTimer = setTimeout(function() {
      if (self.longPressTimer !== null) {
        window.v.presssed = false;
        self.longPressTimer = null;
        console.log('长按交互结束');
      }
    }, ms);
  }

  simulateSwipe(direction, duration) {
    const dir = direction || 'left';
    const dur = duration || 1000;
    
    console.log('simulateSwipe 调用, direction:', dir, 'duration:', dur);
    
    if (!window.i || !window.i.freeCamera) {
      console.error('相机未初始化');
      return;
    }

    const camera = window.i.freeCamera;
    
    switch(dir) {
      case 'left': 
        this.setTargetTheta(this.deltaTheta);
        break;
      case 'right': 
        this.setTargetTheta(-this.deltaTheta);
        break;
      case 'up': 
        this.setTargetPhi(-this.deltaPhi);
        break;
      case 'down': 
        this.setTargetPhi(this.deltaPhi);
        break;
    }
    
    camera._tempRotateSmoothing = 1;
    camera._tempSmoothing = 1;
    
    console.log('滑动交互已触发');
  }

  simulateInteraction() {
    console.log('simulateInteraction 调用');
    this.simulateLongPress(2000);
    
    setTimeout(() => {
      this.simulateSwipe('left', 1500);
      
      setTimeout(() => {
        this.simulateSwipe('right', 1500);
        
        setTimeout(() => {
          this.simulateLongPress(2000);
        }, 2000);
      }, 2000);
    }, 3000);
  }

  autoTriggerInteraction(interval) {
    console.log('autoTriggerInteraction 调用, interval:', interval);
    this.simulateInteraction();
    
    if (this.interactionTimer) clearInterval(this.interactionTimer);
    this.interactionTimer = setInterval(() => this.simulateInteraction(), interval);
  }

  stopAutoTrigger() {
    console.log('stopAutoTrigger 调用');
    if (this.interactionTimer) {
      clearInterval(this.interactionTimer);
      this.interactionTimer = null;
      console.log('已停止自动触发');
    }
  }

  getCurrentState() { 
    if (!window.v) return null;
    return window.v.state; 
  }
  
  setState(state) { 
    if (!window.v) return;
    window.v.state = state;
  }
  
  getColorBodyIndex() { 
    if (!window.v) return null;
    return window.v.colorBodyIndex; 
  }
  
  setColorBodyIndex(index) { 
    if (!window.v) return;
    window.v.colorBodyIndex = index;
  }
  
  getColorInteriorIndex() { 
    if (!window.v) return null;
    return window.v.colorInteriorIndex; 
  }
  
  setColorInteriorIndex(index) { 
    if (!window.v) return;
    window.v.colorInteriorIndex = index;
  }
  
  resetCamera() {
    if (!window.i || !window.i.freeCamera) {
      console.log('相机不可用');
      return;
    }
    
    const camera = window.i.freeCamera;
    camera._targetTheta = 0;
    camera._targetPhi = Math.PI / 2;
    camera._targetSpringLength = 8;
    camera._targetLookAt.set(0, 1, 0);
    camera._targetFov = 33.4;
    camera._tempRotateSmoothing = 3;
    camera._tempSmoothing = 3;
    
    console.log('相机已重置到初始位置');
  }

  testSwipeAngle() {
    console.log('=== 开始测试旋转角度 ===');
    
    const testCases = [
      { name: '单次向左', calls: ['left'] },
      { name: '连续两次向左', calls: ['left', 'left'] },
      { name: '连续四次向左', calls: ['left', 'left', 'left', 'left'] },
      { name: '连续两次向右', calls: ['right', 'right'] },
      { name: '混合方向', calls: ['left', 'right', 'left'] }
    ];
    
    const self = this;
    let testIndex = 0;
    
    function runNextTest() {
      if (testIndex >= testCases.length) {
        console.log('=== 测试完成 ===');
        return;
      }
      
      const test = testCases[testIndex];
      console.log('\n--- 测试: ' + test.name + ' ---');
      
      self.resetCamera();
      
      setTimeout(() => {
        test.calls.forEach((dir, i) => {
          setTimeout(() => {
            console.log('执行第' + (i+1) + '次 ' + dir);
            self.simulateSwipe(dir, 500);
          }, i * 1000);
        });
        
        setTimeout(() => {
          console.log('最终_targetTheta:', self.getTargetTheta().toFixed(3));
          console.log('最终_targetPhi:', self.getTargetPhi().toFixed(3));
          testIndex++;
          setTimeout(runNextTest, 1000);
        }, test.calls.length * 1000 + 1000);
      }, 1500);
    }
    
    runNextTest();
  }
}

console.log('=== 创建 InteractionController 实例 ===');
const interactionController = new InteractionController();

window.interactionController = interactionController;

window.simulateLongPress = function(duration) {
  interactionController.simulateLongPress(duration);
};

window.simulateSwipe = function(direction, duration) {
  interactionController.simulateSwipe(direction, duration);
};

window.simulateInteraction = function() {
  interactionController.simulateInteraction();
};

window.autoTriggerInteraction = function(interval) {
  interactionController.autoTriggerInteraction(interval);
};

window.stopAutoTrigger = function() {
  interactionController.stopAutoTrigger();
};

window.resetCamera = function() {
  interactionController.resetCamera();
};

window.testSwipeAngle = function() {
  interactionController.testSwipeAngle();
};

window.setDeltaTheta = function(delta) {
  interactionController.deltaTheta = delta;
  console.log('deltaTheta已设置为:', delta);
};

window.setDeltaPhi = function(delta) {
  interactionController.deltaPhi = delta;
  console.log('deltaPhi已设置为:', delta);
};

window.getCurrentCameraAngles = function() {
  return interactionController.getCurrentCameraAngles();
};

console.log('=== Interaction.js 加载完成 ===');