vgm_psg2psg.exe level3.vgm
prepare4SN.exe level3.vgm_ton00.60hz level3.vgm_ton01.60hz level3.vgm_ton02.60hz level3.vgm_noi03.60hz level3.psgsn
vgmcomp2.exe -sn level3.psgsn level3.sbf

vgm_psg2psg.exe shot1.vgm
prepare4SN.exe shot1.vgm_ton00.60hz - - - shot1.psgsn
vgmcomp2.exe -sn shot1.psgsn shot1.sbf
