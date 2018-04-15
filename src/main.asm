COPY    START   0
FIRST   MOV     RETADR,%RL
        MOV     %RB,#LENGTH
        BASE    LENGTH
CLOOP  +JSUB    RDREC
        MOV     %RA,LENGTH
        COMP    #0
        JEQ     ENDFIL
       +JSUB    WRREC
        J       CLOOP
ENDFIL  MOV     %RA,EOF
        MOV     BUFFER,%RA
        MOV     %RA,#3
        MOV     LENGTH,%RA
       +JSUB    WRREC
        J       @RETADR
EOF     BYTE    C'EOF'
RETADR  RESW    1
LENGTH  RESW    1
BUFFER  RESB    4096
RDREC   CLEAR   %RX
        CLEAR   %RA
        CLEAR   %RS
       +MOV     %RT,#4096
RLOOP   TD      INPUT
        JEQ     RLOOP
        RD      INPUT
        COMPR   %RA,%RS
        JEQ     EXIT
        STCH    BUFFER[%RX]
        TIXR    %RT
        JLT     RLOOP
EXIT    MOV     LENGTH,%RX
        RSUB
INPUT   BYTE    X'F3'
WRREC   CLEAR   %RX
        MOV     %RT,LENGTH
WLOOP   TD      OUTPUT
        JEQ     WLOOP
        LDCH    BUFFER[%RX]
        WD      OUTPUT
        TIXR    %RT
        JLT     WLOOP
        RSUB
OUTPUT  BYTE    X'05'
        END     FIRST