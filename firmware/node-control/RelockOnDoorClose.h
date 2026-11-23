#pragma once

namespace ControlPolicy {

class RelockOnDoorClose {
 public:
  void arm() {
    armed_ = true;
    sawOpen_ = false;
  }

  void cancel() { armed_ = false; }

  bool update(bool sensorKnown, bool doorClosed) {
    if (!armed_ || !sensorKnown) return false;
    if (!doorClosed) sawOpen_ = true;
    return sawOpen_ && doorClosed;
  }

 private:
  bool armed_ = false;
  bool sawOpen_ = false;
};

}  // namespace ControlPolicy
