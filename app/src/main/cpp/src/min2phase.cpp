/**
 * min2phaseCXX Copyright (C) 2022 Borgo Federico
 * This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.
 * This is free software, and you are welcome to redistribute it
 * under certain conditions; type `show c' for details.
 */

#include <fstream>
#include <min2phase/min2phase.h>
#include "Search.h"

namespace min2phase {

    void init() {
        info::init();
        coords::init();
    }

    bool writeFile(const std::string& name){
        if(!coords::isInit())
            init();

        std::ofstream out(name);

        MIN2PHASE_OUTPUT("Writing file.")

        if(out) {
            out.write(reinterpret_cast<char *>(&coords::coords), sizeof(coords::coords_t));
            out.close();
            return true;
        }

        return false;
    }

    bool loadFile(const std::string& name){
        std::ifstream in(name);

        MIN2PHASE_OUTPUT("Loading file.")

        if(in){
            in.read(reinterpret_cast<char*>(&coords::coords), sizeof(coords::coords_t));
            in.close();
            info::init();
            return true;
        }
        else
            init();

        return false;
    }

    // 调用 Search().solve 会直接派发到多核协调器中，并发启动多核心协同求解。
    std::string solve(const std::string &facelets, int8_t maxDepth, int32_t probeMax, int32_t probeMin,
                      int8_t verbose, uint8_t* usedMoves, SearchCallback* callback) {
        MIN2PHASE_OUTPUT("Init solving.")
        return Search().solve(facelets, maxDepth, probeMax, probeMin, verbose, usedMoves, callback);
    }

    // Android 环境不需要网页服务端，直接返回 false
    bool server(uint16_t port, uint16_t mReq){
        return false;
    }

    // Android 环境直接返回 false
    bool stop(){
        return false;
    }

    // Android 环境直接返回空
    std::string webSearch(const std::string& ip, int32_t port,
                          const std::string &facelets, int8_t maxDepth,
                          int32_t probeMax, int32_t probeMin,
                          int8_t verbose, uint8_t* usedMoves,
                          std::string *time){
        return "";
    }
}
