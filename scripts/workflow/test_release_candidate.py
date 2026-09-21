"""Exercise immutable transfer and the existing promotion boundary without publishing."""
import copy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import release_candidate as rc
import plugin_compatibility as pc
import branch_zip


class ReleaseTest(unittest.TestCase):
    def test_bundle_binds_source_zip_both_reports_and_excludes_private_log(self):
        policy = json.loads(pc.POLICY.read_text())
        source = 'a' * 40
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); directory = root / 'candidate'; directory.mkdir()
            product = directory / rc.version_name('1.2.3')
            jar = io.BytesIO()
            with zipfile.ZipFile(jar, 'w') as inner:
                inner.writestr('META-INF/plugin.xml', '<idea-plugin><id>com.cursoragent.plugin</id><version>1.2.3</version></idea-plugin>')
                inner.writestr('cursor-agent-build.properties', f'plugin.version=1.2.3\nsource.commit={source}\nsource.state=clean\nsdk.build={policy["targets"]["quail1"]["build"]}\njvm.target=21\n')
                inner.writestr('Example.class', b'bytecode')
            with zipfile.ZipFile(product, 'w') as outer: outer.writestr('plugin/lib/plugin.jar', jar.getvalue())
            manifest=pc.seal(product, source, policy)
            rc.write(directory/'manifest.json',manifest)
            rc.write(directory/'inputs.json',{'source':source,'version':'1.2.3','files':{},'libraries':['plugin.jar'],'command':rc.build_command('1.2.3'),'java_version':'21.0.11'})
            evidence={}
            for key,target in policy['targets'].items():
                path=root/key; reports=path/'reports'/target['build']/'plugins'/pc.PLUGIN_ID/'1.2.3'; reports.mkdir(parents=True)
                (reports/'verification-verdict.txt').write_text('Compatible.')
                (reports/'telemetry.txt').write_text('Verified classes in plugin artifact: 1\n')
                (reports/'dependencies.txt').write_text(pc.PLUGIN_ID+':1.2.3\n')
                log='Starting the IntelliJ Plugin Verifier 1.410\nScheduled verifications (1):\nFinished 1 of 1 verifications\nSDK /Users/private-user/sdk\n'
                (path/'verifier.log').write_text(log)
                result={'status':'passed','artifact':manifest,'verifier_version':'1.410',
                        'target':{'build':target['build'],'java_version':target['java_version'], 'distribution_version':target['version'],
                                  'java_runtime':'Picked up JAVA_TOOL_OPTIONS: -Dsecret=fake-test-value\nopenjdk version "'+target['java_version']+'"\nOpenJDK Runtime Environment (build 21.0.10+-123-b1.1)\nOpenJDK 64-Bit Server VM (build 21.0.10+-123-b1.1, mixed mode)'},
                        **pc.check_reports(path/'reports',log,target,manifest,policy)}
                rc.write(path/'result.json',result); evidence[key]=path
            with patch.object(rc,'source_inputs',return_value={}),patch.object(rc,'git_read',return_value=json.dumps(policy)):
                rc.bundle(directory,manifest['sha256'],evidence)
                original = pc.digest(product)
                rc.bundle(directory,manifest['sha256'],evidence)  # Same reports can safely retry.
                (directory/'compatibility-quail4.zip').unlink()  # Simulate interrupted copy.
                (directory/'bundle.json').unlink()
                rc.bundle(directory,manifest['sha256'],evidence)
                self.assertEqual(pc.digest(product),original)
                self.assertEqual(rc.validate_bundle(directory,manifest['sha256']),manifest)
                with zipfile.ZipFile(directory/'compatibility-quail1.zip') as report:
                    self.assertNotIn('/Users/',report.read('verifier-summary.txt').decode())
                    self.assertNotIn('fake-test-value',report.read('result.json').decode())
                inputs=rc.read(directory/'inputs.json'); inputs['java_version']='21.0.11\nPicked up JAVA_TOOL_OPTIONS: fake-test-value'
                rc.write(directory/'inputs.json',inputs)
                with self.assertRaisesRegex(ValueError,'public build inputs'):rc.candidate(directory,manifest['sha256'])
                inputs['java_version']='21.0.11'; rc.write(directory/'inputs.json',inputs)
                with self.assertRaises(ValueError):rc.validate_bundle(directory,'0'*64)
                product.write_bytes(b'changed')
                with self.assertRaises(ValueError):rc.validate_bundle(directory,manifest['sha256'])
            for version in ('1.2.3-SNAPSHOT','../v1','01.2.3'):
                with self.assertRaises(ValueError):rc.version_name(version)

    def test_preserve_retries_exact_bytes_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp); (path/'a.zip').write_bytes(b'original')
            state={'release':None,'assets':[],'bytes':{}}; mutations=[]
            def api(endpoint,method='GET',data=None):
                mutations.append((endpoint,method))
                if endpoint=='releases':state['release']={**data,'id':1};return state['release']
                if endpoint=='releases/1':state['release'].update(data);return state['release']
                raise AssertionError(endpoint)
            def upload(*args):
                self.assertNotIn('--clobber',args)
                state['bytes'][2]=(path/'a.zip').read_bytes()
                asset={'id':2,'name':'a.zip','state':'uploaded','digest':'sha256:'+pc.digest(path/'a.zip')}
                state['assets'].append(asset);return json.dumps(asset)
            def download(asset,destination):destination.write_bytes(state['bytes'][asset['id']])
            with patch.object(rc,'release_for',side_effect=lambda _:state['release']),patch.object(rc.github,'api',side_effect=api),patch.object(rc.github,'pages',side_effect=lambda _:state['assets']),patch.object(rc.github,'gh',side_effect=upload),patch.object(rc,'download_asset',side_effect=download),patch.dict(rc.os.environ,{'GITHUB_REPOSITORY':'owner/repo'}):
                for _ in range(2):rc.preserve('plugin-rc-test','main',path,['a.zip'],True,'body')
                self.assertEqual(len(state['assets']),1)
                self.assertNotIn('DELETE',[m for _,m in mutations])
                (path/'a.zip').write_bytes(b'changed')
                with self.assertRaisesRegex(ValueError,'no overwrite'):rc.preserve('plugin-rc-test','main',path,['a.zip'],True,'body')
                self.assertEqual(state['bytes'][2],b'original')
                self.assertIsNone(branch_zip.cleanup_identity(state['release']))
                state['release']['prerelease']=False
                self.assertIsNone(branch_zip.cleanup_identity(state['release']))

    def test_publication_requires_merged_promotion_matching_candidate_hash_and_checks(self):
        candidate='a'*40; head='b'*40; base='c'*40; merge='d'*40; sha='e'*64
        manifest={'identity':{'source.commit':candidate}}
        pr={'merged':True,'head':{'sha':head,'repo':{'full_name':'owner/repo'}},'base':{'ref':'main','repo':{'full_name':'owner/repo'}},'merge_commit_sha':merge}
        promotion={'artifact_sha256':sha}
        gate={'mode':'promotion','gui_complete':True,'candidate':candidate}
        checks=[{'name':k,'state':'SUCCESS'} for k in ('test','PR policy','Agent review','Acceptance gate')]
        def git(*args):
            if args[0]=='rev-list':return f'{merge} {base} {head}'
            if args[0]=='show':return json.dumps(promotion)
            return ''
        with patch.dict(rc.os.environ,{'GITHUB_REPOSITORY':'owner/repo'}),patch.object(rc,'validate_bundle',return_value=manifest),patch.object(rc.github,'api',return_value=pr),patch.object(rc,'git_read',side_effect=git),patch.object(rc,'verify_pr',return_value=gate) as verify,patch.object(rc.github,'gh',side_effect=lambda *a:json.dumps(checks)):
            self.assertEqual(rc.publication(Path('.'),sha,1),(manifest,merge))
            self.assertEqual(verify.call_args[0][0]['base']['sha'],base)
            for obj,key,value in ((pr,'merged',False),(promotion,'artifact_sha256','f'*64),(gate,'candidate','f'*40),(gate,'mode','tooling')):
                old=obj[key];obj[key]=value
                with self.assertRaises(ValueError):rc.publication(Path('.'),sha,1)
                obj[key]=old
            checks.pop()
            with self.assertRaisesRegex(ValueError,'checks'):rc.publication(Path('.'),sha,1)
            with patch.object(rc,'publication',side_effect=ValueError('missing acceptance')),patch.object(rc,'preserve') as preserve:
                with self.assertRaises(ValueError):rc.publish(Path('.'),sha,1)
                preserve.assert_not_called()
