'use strict';
/**
 * Garante o arquivo da atualização automática (electron-updater) ao lado do instalador:
 * latest.yml (Windows, .exe) ou latest-linux.yml (Linux, .deb).
 * O electron-builder costuma gerá-lo; se não gerou (build com --publish never), cria aqui
 * com o mesmo formato: versão, arquivo, sha512 em base64 e tamanho.
 *
 * Uso: node scripts/garantir-latest-yml.js [win|linux]   (padrão: win)
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const alvo = process.argv[2] === 'linux' ? 'linux' : 'win';
const pasta = path.join(__dirname, '..', 'release-dist');
const versao = require('../package.json').version;
const nome = alvo === 'linux' ? `SGE-Diario-Desktop-${versao}-amd64.deb` : `SGE-Diario-Desktop-Setup-${versao}.exe`;
const instalador = path.join(pasta, nome);
const destino = path.join(pasta, alvo === 'linux' ? 'latest-linux.yml' : 'latest.yml');

if (!fs.existsSync(instalador)) { console.error(`Instalador não encontrado: ${instalador}`); process.exit(1); }
if (fs.existsSync(destino) && fs.readFileSync(destino, 'utf8').includes(nome)) { console.log(`${path.basename(destino)} já existe.`); process.exit(0); }
const sha512 = crypto.createHash('sha512').update(fs.readFileSync(instalador)).digest('base64');
const tamanho = fs.statSync(instalador).size;
fs.writeFileSync(destino, [
  `version: ${versao}`,
  'files:',
  `  - url: ${nome}`,
  `    sha512: ${sha512}`,
  `    size: ${tamanho}`,
  `path: ${nome}`,
  `sha512: ${sha512}`,
  `releaseDate: '${new Date().toISOString()}'`,
  '',
].join('\n'));
console.log(`${path.basename(destino)} criado para ${nome}`);
