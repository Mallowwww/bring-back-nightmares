{ pkgs ? import <nixpkgs> {} }:
pkgs.mkShell {
  packages = [ pkgs.jdk21 ];
  shellHook = ''
    export JAVA_HOME=${pkgs.jdk21.home}
  '';
}